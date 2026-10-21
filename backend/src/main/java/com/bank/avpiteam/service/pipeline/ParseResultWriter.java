package com.bank.avpiteam.service.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Запись результатов разбора назначения платежа в operation_parse_result.
 * Имя пишется в extracted_name: от него в БД считается extracted_name_norm,
 * по которому маппинг ищет выгодоприобретателя.
 */
@Repository
@RequiredArgsConstructor
public class ParseResultWriter {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param parser             MARKER (regex в процессе) или NLP (Python-сервис)
     * @param classificationMethod MARKER / SEMANTIC / NLP_NER (V3)
     * @param confidence         0..1 или null
     */
    public record ParseRecord(
            long operationId,
            String parser,
            String parserVersion,
            boolean thirdParty,
            Boolean genuineThirdParty,
            String extractedName,
            Double confidence,
            String classificationMethod,
            Double classificationScore
    ) {
    }

    public void insert(List<ParseRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        SqlParameterSource[] rows = records.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("operationId", r.operationId())
                        .addValue("parser", r.parser())
                        .addValue("parserVersion", r.parserVersion())
                        .addValue("thirdParty", r.thirdParty())
                        // извлечённое лицо считаем выгодоприобретателем, если это не сам плательщик/получатель
                        .addValue("partyRole", Boolean.TRUE.equals(r.genuineThirdParty()) ? "BENEFICIARY" : null)
                        .addValue("extractedName", r.extractedName())
                        .addValue("confidence", clamp(r.confidence()))
                        .addValue("method", r.classificationMethod())
                        .addValue("score", finite(r.classificationScore()))
                        .addValue("genuine", r.genuineThirdParty()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO operation_parse_result (
                    operation_id, parser, parser_version, is_third_party, party_role,
                    extracted_name, confidence, classification_method, classification_score,
                    is_genuine_third_party)
                VALUES (
                    :operationId, :parser, :parserVersion, :thirdParty, :partyRole,
                    :extractedName, :confidence, :method, :score,
                    :genuine)
                """, rows);
    }

    // confidence ограничен CHECK 0..1, а score семантического классификатора - косинусная близость
    private static Double clamp(Double value) {
        Double finite = finite(value);
        return finite == null ? null : Math.max(0.0, Math.min(1.0, finite));
    }

    private static Double finite(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? null : value;
    }
}
