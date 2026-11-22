package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.service.mapping.MappingCard.PartyInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Стороны договоров одним запросом на набор договоров (без запроса на каждую строку). */
@Repository
@RequiredArgsConstructor
public class ContractPartiesReader {

    private final NamedParameterJdbcTemplate jdbc;

    /** Договор -> стороны, отсортированные по роли и id клиента. */
    public Map<Long, List<PartyInfo>> partiesOf(Collection<Long> contractIds) {
        Map<Long, List<PartyInfo>> result = new HashMap<>();
        if (contractIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT cp.contract_id, cp.role, cl.id AS client_id, cl.inn,
                       coalesce(cl.organization_name,
                                concat_ws(' ', cl.last_name, cl.first_name, cl.middle_name)) AS name
                FROM contract_parties cp
                JOIN clients cl ON cl.id = cp.client_id
                WHERE cp.contract_id IN (:ids)
                ORDER BY cp.contract_id, cp.role, cl.id
                """, Map.of("ids", List.copyOf(contractIds)), rs -> {
            result.computeIfAbsent(rs.getLong("contract_id"), k -> new ArrayList<>())
                    .add(new PartyInfo(rs.getString("role"), rs.getLong("client_id"),
                            rs.getString("name"), rs.getString("inn")));
        });
        return result;
    }
}
