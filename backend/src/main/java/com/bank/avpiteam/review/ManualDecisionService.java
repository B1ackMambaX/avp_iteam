package com.bank.avpiteam.review;

import com.bank.avpiteam.domain.Operation.OperationStatus;
import com.bank.avpiteam.review.ManualDecisionException.Code;
import com.bank.avpiteam.service.mapping.MappingCard;
import com.bank.avpiteam.service.mapping.MappingQueryService;
import com.bank.avpiteam.stats.OperationsChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Ручное решение по маппингу (BE-4). Правила (согласованы):
 *  - решение принимается только из очереди: AMBIGUOUS, MANUAL_REQUIRED; принятое не исправляется;
 *  - подтверждение (contractId != null) -> MANUAL_MAPPED, отклонение (contractId = null) -> NOT_FOUND;
 *  - договор не из сохранённых кандидатов допустим, но только с комментарием;
 *  - комментарий до 1000 символов, обязателен при отклонении и при договоре не из кандидатов;
 *  - источник MANUAL, автор - из Spring Security, время - now(); история - operation_manual_decision.
 * Конвейер такие операции не трогает: он забирает только PENDING и PARSED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualDecisionService {

    public static final int MAX_COMMENT_LENGTH = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final MappingQueryService mappingQueryService;
    private final ApplicationEventPublisher events;

    private record Locked(OperationStatus status, long version) {
    }

    /** @return обновлённая карточка операции */
    @Transactional
    @PreAuthorize("hasAuthority('EDIT_MAPPING')")
    public MappingCard resolve(ResolveCommand cmd) {
        String comment = normalizeComment(cmd.comment());
        String author = SecurityContextHolder.getContext().getAuthentication().getName();

        // Блокировка строки: второй редактор ждёт коммита первого и видит уже новую версию
        List<Locked> rows = jdbc.query("SELECT status, " + MappingQueryService.VERSION_SQL + " AS version "
                        + "FROM operations o WHERE o.id = :id FOR UPDATE",
                Map.of("id", cmd.operationId()),
                (rs, i) -> new Locked(OperationStatus.valueOf(rs.getString("status")), rs.getLong("version")));
        if (rows.isEmpty()) {
            throw new ManualDecisionException(Code.OPERATION_NOT_FOUND, "Operation not found: " + cmd.operationId());
        }
        Locked op = rows.get(0);
        if (cmd.expectedVersion() != null && cmd.expectedVersion() != op.version()) {
            throw new ManualDecisionException(Code.STALE_CARD,
                    "Operation " + cmd.operationId() + " was changed after the card was read");
        }
        if (!OperationQueryService.REVIEW_QUEUE.contains(op.status())) {
            throw new ManualDecisionException(Code.FORBIDDEN_TRANSITION,
                    "Manual decision is not allowed for status " + op.status());
        }

        boolean fromCandidates = false;
        if (cmd.contractId() != null) {
            Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM contracts WHERE id = :id)",
                    Map.of("id", cmd.contractId()), Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                throw new ManualDecisionException(Code.CONTRACT_NOT_FOUND, "Contract not found: " + cmd.contractId());
            }
            fromCandidates = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM operation_mapping_candidate
                                   WHERE operation_id = :operationId AND contract_id = :contractId)
                    """, Map.of("operationId", cmd.operationId(), "contractId", cmd.contractId()), Boolean.class));
            if (!fromCandidates && comment == null) {
                throw new ManualDecisionException(Code.CONTRACT_NOT_ALLOWED,
                        "Contract " + cmd.contractId() + " is not among candidates: a comment is required");
            }
        } else if (comment == null) {
            throw ValidationFailedException.of("comment", "required when rejecting");
        }

        OperationStatus newStatus = cmd.contractId() != null ? OperationStatus.MANUAL_MAPPED : OperationStatus.NOT_FOUND;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", cmd.operationId())
                .addValue("status", newStatus.name())
                .addValue("contractId", cmd.contractId())
                .addValue("author", author)
                .addValue("comment", comment)
                .addValue("action", cmd.contractId() != null ? "CONFIRM" : "REJECT")
                .addValue("fromCandidates", fromCandidates)
                .addValue("previousStatus", op.status().name());
        jdbc.update("""
                UPDATE operations
                SET status = :status, contract_id = :contractId, mapping_source = 'MANUAL',
                    mapped_by = :author, mapped_at = now(), updated_at = now()
                WHERE id = :id
                """, params);
        jdbc.update("""
                INSERT INTO operation_manual_decision
                    (operation_id, action, contract_id, from_candidates, previous_status, comment, decided_by)
                VALUES (:id, :action, :contractId, :fromCandidates, :previousStatus, :comment, :author)
                """, params);

        log.info("Ручное решение: операция {} {} -> {}, договор {}, автор {}",
                cmd.operationId(), op.status(), newStatus, cmd.contractId(), author);
        events.publishEvent(new OperationsChangedEvent("manual-decision"));
        return mappingQueryService.findCard(cmd.operationId()).orElseThrow();
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.strip();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw ValidationFailedException.of("comment", "must be at most " + MAX_COMMENT_LENGTH + " chars");
        }
        return trimmed;
    }
}
