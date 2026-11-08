package com.bank.avpiteam.service.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

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
}
