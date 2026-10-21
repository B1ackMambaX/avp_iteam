package com.bank.avpiteam.service.pipeline;

import com.bank.avpiteam.config.PipelineProperties;
import com.bank.avpiteam.service.mapping.Normalization;
import com.bank.avpiteam.service.parsing.ParsedPaymentPurpose;
import com.bank.avpiteam.service.parsing.PaymentPurposeParser;
import com.bank.avpiteam.service.pipeline.ParseResultWriter.ParseRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Приём новых (PENDING) операций в конвейер.
 *  NLP    - ставит операции в очередь nlp_task, статус WAITING_NLP;
 *  MARKER - сразу разбирает назначение regex-парсером, статус PARSED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PipelineProperties properties;
    private final PaymentPurposeParser markerParser;
    private final ParseResultWriter parseResultWriter;

    /** @return сколько операций принято */
    @Transactional
    public int intakeBatch() {
        return properties.getParser() == PipelineProperties.ParserMode.NLP
                ? enqueueForNlp()
                : parseWithMarker();
    }

    private int enqueueForNlp() {
        // Повторная постановка (операцию вернули в PENDING) переиспользует строку задачи
        int queued = jdbc.update("""
                WITH picked AS (
                    SELECT id FROM operations
                    WHERE status = 'PENDING'
                    ORDER BY id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                ), queued AS (
                    INSERT INTO nlp_task (operation_id)
                    SELECT id FROM picked
                    ON CONFLICT (operation_id) DO UPDATE
                        SET status = 'NEW', attempts = 0, next_try_at = now(),
                            locked_until = NULL, last_error = NULL, updated_at = now()
                )
                UPDATE operations o
                SET status = 'WAITING_NLP', updated_at = now()
                FROM picked p
                WHERE o.id = p.id
                """,
                Map.of("limit", properties.getIntakeBatchSize()));
        if (queued > 0) {
            log.debug("В очередь NLP поставлено {} операций", queued);
        }
        return queued;
    }

    private record PendingRow(long id, String purpose, String senderName, String receiverName) {
    }

    private int parseWithMarker() {
        List<PendingRow> rows = jdbc.query("""
                SELECT id, payment_purpose, sender_name, receiver_name
                FROM operations
                WHERE status = 'PENDING'
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """,
                Map.of("limit", properties.getIntakeBatchSize()),
                (rs, i) -> new PendingRow(rs.getLong("id"), rs.getString("payment_purpose"),
                        rs.getString("sender_name"), rs.getString("receiver_name")));
        if (rows.isEmpty()) {
            return 0;
        }

        List<ParseRecord> records = new ArrayList<>(rows.size());
        for (PendingRow row : rows) {
            ParsedPaymentPurpose parsed = markerParser.parse(row.purpose());
            boolean genuine = parsed.thirdParty()
                    && parsed.beneficiaryName() != null
                    && isNotOperationParty(parsed.beneficiaryName(), row.senderName(), row.receiverName());
            records.add(new ParseRecord(
                    row.id(), "MARKER", properties.getMarkerParserVersion(),
                    parsed.thirdParty(), genuine, parsed.beneficiaryName(),
                    parsed.confidence(), "MARKER", parsed.confidence()));
        }
        parseResultWriter.insert(records);
        jdbc.update("UPDATE operations SET status = 'PARSED', updated_at = now() WHERE id IN (:ids)",
                Map.of("ids", rows.stream().map(PendingRow::id).toList()));
        return rows.size();
    }

    /**
     * Сверка из ответа заказчика (вопрос 6): если извлечённое имя совпадает
     * с отправителем или получателем, это не третье лицо, а ложное срабатывание.
     * То же правило, что в BeneficiaryDetectionServiceImpl для NLP-разбора.
     */
    static boolean isNotOperationParty(String extractedName, String senderName, String receiverName) {
        String name = Normalization.fullName(extractedName);
        return !Objects.equals(name, Normalization.fullName(senderName))
                && !Objects.equals(name, Normalization.fullName(receiverName));
    }
}
