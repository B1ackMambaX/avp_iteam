package com.bank.avpiteam.service.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Пакетные запросы маппинга. Работает через JDBC, а не JPA: на пачку операций -
 * фиксированное число запросов по индексам, без загрузки сущностей.
 */
@Repository
@RequiredArgsConstructor
public class MappingJdbcRepository {

    // Длинные IN-списки режем на части, чтобы не упереться в лимит параметров JDBC
    private static final int IN_CHUNK = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record OperationRow(long id, String senderInn, String receiverInn, String paymentPurpose) {
    }

    public record ParseRow(boolean thirdParty, Boolean genuineThirdParty, String extractedNameNorm) {

        public boolean isGenuineThirdParty() {
            return genuineThirdParty != null ? genuineThirdParty : thirdParty;
        }
    }

    public record OperationUpdate(long operationId, String status, Long contractId, String reason) {
    }

    /** Забирает пачку PARSED-операций; параллельный маппинг пропускает уже взятые строки. */
    public List<OperationRow> claimParsed(int limit) {
        return jdbc.query("""
                SELECT id, sender_inn, receiver_inn, payment_purpose
                FROM operations
                WHERE status = 'PARSED'
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """,
                Map.of("limit", limit),
                (rs, i) -> new OperationRow(rs.getLong("id"), rs.getString("sender_inn"),
                        rs.getString("receiver_inn"), rs.getString("payment_purpose")));
    }

    /** Последний результат разбора назначения по каждой операции. */
    public Map<Long, ParseRow> latestParse(Collection<Long> operationIds) {
        Map<Long, ParseRow> result = new HashMap<>();
        forEachChunk(operationIds, chunk -> jdbc.query("""
                SELECT DISTINCT ON (operation_id)
                       operation_id, is_third_party, is_genuine_third_party, extracted_name_norm
                FROM operation_parse_result
                WHERE operation_id IN (:ids)
                ORDER BY operation_id, parsed_at DESC, id DESC
                """,
                Map.of("ids", chunk),
                rs -> {
                    boolean genuineNull = rs.getObject("is_genuine_third_party") == null;
                    result.put(rs.getLong("operation_id"), new ParseRow(
                            rs.getBoolean("is_third_party"),
                            genuineNull ? null : rs.getBoolean("is_genuine_third_party"),
                            rs.getString("extracted_name_norm")));
                }));
        return result;
    }

    /** ИНН -> id клиентов (ИНН не уникален). */
    public Map<String, List<Long>> clientIdsByInn(Collection<String> inns) {
        Map<String, List<Long>> result = new HashMap<>();
        forEachChunk(inns, chunk -> jdbc.query(
                "SELECT id, inn FROM clients WHERE inn IN (:inns)",
                Map.of("inns", chunk),
                rs -> {
                    result.computeIfAbsent(rs.getString("inn"), k -> new ArrayList<>()).add(rs.getLong("id"));
                }));
        return result;
    }

    /**
     * Клиент -> договоры, где он сторона (в любой роли).
     * Клиенты с числом договоров больше maxContracts пропускаются целиком.
     */
    public Map<Long, Set<Long>> contractsByPartyClients(Collection<Long> clientIds, int maxContracts) {
        Map<Long, Set<Long>> result = new HashMap<>();
        forEachChunk(clientIds, chunk -> jdbc.query("""
                WITH hubs AS (
                    SELECT client_id FROM contract_parties
                    WHERE client_id IN (:ids)
                    GROUP BY client_id
                    HAVING count(*) > :max
                )
                SELECT client_id, contract_id FROM contract_parties
                WHERE client_id IN (:ids)
                  AND client_id NOT IN (SELECT client_id FROM hubs)
                """,
                new MapSqlParameterSource("ids", chunk).addValue("max", maxContracts),
                rs -> {
                    result.computeIfAbsent(rs.getLong("client_id"), k -> new HashSet<>()).add(rs.getLong("contract_id"));
                }));
        return result;
    }

    /** Нормализованный номер -> договоры (номер не уникален). */
    public Map<String, Set<Long>> contractsByNumberNorm(Collection<String> numbers) {
        Map<String, Set<Long>> result = new HashMap<>();
        forEachChunk(numbers, chunk -> jdbc.query(
                "SELECT id, contract_number_norm FROM contracts WHERE contract_number_norm IN (:numbers)",
                Map.of("numbers", chunk),
                rs -> {
                    result.computeIfAbsent(rs.getString("contract_number_norm"), k -> new HashSet<>()).add(rs.getLong("id"));
                }));
        return result;
    }

    /**
     * Нормализованное ФИО -> договоры, где клиент с таким ФИО - выгодоприобретатель.
     * ФИО, у которого тёзок больше maxClients, пропускается: такое совпадение ничего не доказывает.
     */
    public Map<String, Set<Long>> beneficiaryContractsByName(Collection<String> names, int maxClients) {
        Map<String, Set<Long>> result = new HashMap<>();
        forEachChunk(names, chunk -> jdbc.query("""
                WITH matched AS (
                    SELECT id, full_name_norm FROM clients WHERE full_name_norm IN (:names)
                ),
                common AS (
                    SELECT full_name_norm FROM matched GROUP BY full_name_norm HAVING count(*) > :max
                )
                SELECT m.full_name_norm, cp.contract_id
                FROM matched m
                JOIN contract_parties cp ON cp.client_id = m.id AND cp.role = 'BENEFICIARY'
                WHERE m.full_name_norm NOT IN (SELECT full_name_norm FROM common)
                """,
                new MapSqlParameterSource("names", chunk).addValue("max", maxClients),
                rs -> {
                    result.computeIfAbsent(rs.getString("full_name_norm"), k -> new HashSet<>()).add(rs.getLong("contract_id"));
                }));
        return result;
    }

    /** Договор -> его стороны (любые роли). Для кандидатов, найденных по номеру или ФИО. */
    public Map<Long, Set<Long>> partiesOfContracts(Collection<Long> contractIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        forEachChunk(contractIds, chunk -> jdbc.query(
                "SELECT contract_id, client_id FROM contract_parties WHERE contract_id IN (:ids)",
                Map.of("ids", chunk),
                rs -> {
                    result.computeIfAbsent(rs.getLong("contract_id"), k -> new HashSet<>()).add(rs.getLong("client_id"));
                }));
        return result;
    }

    /** Заменяет кандидатов операций пачки: повторный маппинг не оставляет старых строк. */
    public void replaceCandidates(Collection<Long> operationIds, Map<Long, List<ScoredCandidate>> candidates) {
        forEachChunk(operationIds, chunk -> jdbc.update(
                "DELETE FROM operation_mapping_candidate WHERE operation_id IN (:ids)",
                Map.of("ids", chunk)));

        List<SqlParameterSource> rows = new ArrayList<>();
        candidates.forEach((operationId, list) -> {
            for (ScoredCandidate candidate : list) {
                rows.add(new MapSqlParameterSource()
                        .addValue("operationId", operationId)
                        .addValue("contractId", candidate.contractId())
                        .addValue("score", candidate.score())
                        .addValue("rules", toJson(candidate.rules())));
            }
        });
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO operation_mapping_candidate (operation_id, contract_id, score, matched_rules)
                    VALUES (:operationId, :contractId, :score, CAST(:rules AS jsonb))
                    """,
                    rows.toArray(SqlParameterSource[]::new));
        }
    }

    public void updateOperations(List<OperationUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        SqlParameterSource[] rows = updates.stream()
                .map(u -> new MapSqlParameterSource()
                        .addValue("id", u.operationId())
                        .addValue("status", u.status())
                        .addValue("contractId", u.contractId())
                        .addValue("reason", u.reason()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                UPDATE operations
                SET status = :status,
                    contract_id = :contractId,
                    mapping_reason = :reason,
                    mapped_by = CASE WHEN CAST(:contractId AS BIGINT) IS NULL THEN NULL ELSE 'AUTO' END,
                    mapped_at = CASE WHEN CAST(:contractId AS BIGINT) IS NULL THEN NULL ELSE now() END,
                    updated_at = now()
                WHERE id = :id
                """, rows);
    }

    private String toJson(Map<MatchRule, Double> rules) {
        Map<String, Double> named = new LinkedHashMap<>();
        rules.forEach((rule, weight) -> named.put(rule.name(), weight));
        try {
            return objectMapper.writeValueAsString(named);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать matched_rules", e);
        }
    }

    private static <T> void forEachChunk(Collection<T> values, java.util.function.Consumer<List<T>> action) {
        if (values.isEmpty()) {
            return;
        }
        List<T> list = new ArrayList<>(values);
        for (int from = 0; from < list.size(); from += IN_CHUNK) {
            action.accept(list.subList(from, Math.min(from + IN_CHUNK, list.size())));
        }
    }
}
