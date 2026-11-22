package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.domain.Operation.OperationStatus;
import com.bank.avpiteam.service.mapping.MappingCard.CandidateInfo;
import com.bank.avpiteam.service.mapping.MappingCard.OperationInfo;
import com.bank.avpiteam.service.mapping.MappingCard.ParseInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Чтение результатов маппинга по операции - для UI и демо:
 * что пришло, как разобрали назначение, какие договоры рассматривались и почему.
 */
@Service
@RequiredArgsConstructor
public class MappingQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractPartiesReader partiesReader;
    private final ObjectMapper objectMapper;

    public record OperationMapping(
            Map<String, Object> operation,
            Map<String, Object> parse,
            List<Map<String, Object>> candidates
    ) {
    }

    public Optional<OperationMapping> find(long operationId) {
        List<Map<String, Object>> operation = jdbc.queryForList("""
                SELECT o.id, o.reference, o.operation_date, o.sender_inn, o.sender_name,
                       o.receiver_inn, o.receiver_name, o.amount, o.currency_code, o.payment_purpose,
                       o.status, o.mapping_reason, o.contract_id, c.contract_number,
                       o.mapped_by, o.mapped_at
                FROM operations o
                LEFT JOIN contracts c ON c.id = o.contract_id
                WHERE o.id = :id
                """, Map.of("id", operationId));
        if (operation.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> parse = jdbc.queryForList("""
                SELECT parser, parser_version, classification_method, classification_score,
                       is_third_party, is_genuine_third_party, extracted_name, extracted_name_norm,
                       party_role, parsed_at
                FROM operation_parse_result
                WHERE operation_id = :id
                ORDER BY parsed_at DESC, id DESC
                LIMIT 1
                """, Map.of("id", operationId));

        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT m.contract_id, c.contract_number, c.contract_type, m.score,
                       m.matched_rules::text AS matched_rules,
                       (SELECT string_agg(cp.role || ': ' || coalesce(cl.organization_name,
                                   concat_ws(' ', cl.last_name, cl.first_name, cl.middle_name))
                                   || coalesce(' (ИНН ' || cl.inn || ')', ''), '; ' ORDER BY cp.role)
                        FROM contract_parties cp JOIN clients cl ON cl.id = cp.client_id
                        WHERE cp.contract_id = m.contract_id) AS parties
                FROM operation_mapping_candidate m
                JOIN contracts c ON c.id = m.contract_id
                WHERE m.operation_id = :id
                ORDER BY m.score DESC, m.contract_id
                """, Map.of("id", operationId));

        return Optional.of(new OperationMapping(operation.get(0), parse.isEmpty() ? null : parse.get(0), candidates));
    }

    // Версия записи для ручного решения: момент последнего изменения в микросекундах
    public static final String VERSION_SQL =
            "(extract(epoch FROM coalesce(o.updated_at, o.created_at)) * 1000000)::bigint";

    /**
     * Типизированная карточка для Vaadin (BE-2). Пусто - операции нет.
     * Кандидаты по убыванию score, затем по contractId.
     */
    @PreAuthorize("hasAuthority('VIEW_DATA')")
    public Optional<MappingCard> findCard(long operationId) {
        List<OperationInfo> operation = jdbc.query("""
                SELECT o.id, o.reference, o.operation_date, o.sender_inn, o.sender_name,
                       o.receiver_inn, o.receiver_name, o.amount, o.currency_code, o.payment_purpose,
                       o.status, o.mapping_reason, o.contract_id, c.contract_number,
                       o.mapped_by, o.mapped_at, o.mapping_source,
                       """ + VERSION_SQL + """
                 AS version
                FROM operations o
                LEFT JOIN contracts c ON c.id = o.contract_id
                WHERE o.id = :id
                """, Map.of("id", operationId), (rs, i) -> new OperationInfo(
                rs.getLong("id"),
                rs.getString("reference"),
                rs.getTimestamp("operation_date").toLocalDateTime(),
                rs.getString("sender_inn"),
                rs.getString("sender_name"),
                rs.getString("receiver_inn"),
                rs.getString("receiver_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                rs.getString("payment_purpose"),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getString("mapping_reason"),
                (Long) rs.getObject("contract_id"),
                rs.getString("contract_number"),
                rs.getString("mapped_by"),
                rs.getObject("mapped_at", OffsetDateTime.class),
                rs.getString("mapping_source"),
                rs.getLong("version")));
        if (operation.isEmpty()) {
            return Optional.empty();
        }

        List<ParseInfo> parse = jdbc.query("""
                SELECT parser, parser_version, classification_method, classification_score,
                       is_third_party, is_genuine_third_party, extracted_name, extracted_name_norm,
                       party_role, parsed_at
                FROM operation_parse_result
                WHERE operation_id = :id
                ORDER BY parsed_at DESC, id DESC
                LIMIT 1
                """, Map.of("id", operationId), (rs, i) -> new ParseInfo(
                rs.getString("parser"),
                rs.getString("parser_version"),
                rs.getString("classification_method"),
                (Double) rs.getObject("classification_score"),
                rs.getBoolean("is_third_party"),
                (Boolean) rs.getObject("is_genuine_third_party"),
                rs.getString("extracted_name"),
                rs.getString("extracted_name_norm"),
                rs.getString("party_role"),
                rs.getObject("parsed_at", OffsetDateTime.class)));

        record CandidateRow(long contractId, String number, String type, BigDecimal score, String rules) {
        }
        List<CandidateRow> rows = jdbc.query("""
                SELECT m.contract_id, c.contract_number, c.contract_type, m.score, m.matched_rules::text AS rules
                FROM operation_mapping_candidate m
                JOIN contracts c ON c.id = m.contract_id
                WHERE m.operation_id = :id
                ORDER BY m.score DESC, m.contract_id
                """, Map.of("id", operationId), (rs, i) -> new CandidateRow(rs.getLong("contract_id"),
                rs.getString("contract_number"), rs.getString("contract_type"),
                rs.getBigDecimal("score"), rs.getString("rules")));
        var parties = partiesReader.partiesOf(rows.stream().map(CandidateRow::contractId).toList());
        List<CandidateInfo> candidates = rows.stream()
                .map(r -> new CandidateInfo(r.contractId(), r.number(), r.type(), r.score(),
                        rules(r.rules()), parties.getOrDefault(r.contractId(), List.of())))
                .toList();

        return Optional.of(new MappingCard(operation.get(0), parse.isEmpty() ? null : parse.get(0), candidates));
    }

    // matched_rules хранится как JSONB {"PARTY_SENDER": 0.4, ...}; неизвестные правила пропускаются
    private Map<MatchRule, Double> rules(String json) {
        Map<MatchRule, Double> result = new EnumMap<>(MatchRule.class);
        if (json == null) {
            return result;
        }
        try {
            Map<String, Double> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            raw.forEach((name, weight) -> {
                try {
                    result.put(MatchRule.valueOf(name), weight);
                } catch (IllegalArgumentException ignored) {
                    // правило из будущей версии маппинга
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный matched_rules: " + json, e);
        }
        return result;
    }
}
