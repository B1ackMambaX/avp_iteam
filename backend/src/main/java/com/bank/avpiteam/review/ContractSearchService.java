package com.bank.avpiteam.review;

import com.bank.avpiteam.service.mapping.ContractPartiesReader;
import com.bank.avpiteam.service.mapping.Normalization;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Поиск договора для ручного выбора (BE-3). Правила совпадения:
 *  - номер договора - точное совпадение после той же нормализации, что в маппинге
 *    (регистр, пробелы, дефисы, "№" не важны: "дг 00000124" = "ДГ-00000124");
 *  - ИНН - точное совпадение с ИНН любой стороны договора (если q - 10 или 12 цифр).
 * Оба поиска - по индексам (contract_number_norm, clients.inn), подстрочного поиска нет.
 * Сортировка: номер договора, затем contractId.
 */
@Service
@RequiredArgsConstructor
public class ContractSearchService {

    public static final int MAX_QUERY_LENGTH = 100;
    private static final Pattern INN = Pattern.compile("\\d{10}|\\d{12}");

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractPartiesReader partiesReader;

    @PreAuthorize("hasAuthority('VIEW_DATA')")
    public PageResult<ContractRow> search(String q, Integer page, Integer size) {
        String query = q == null ? "" : q.strip();
        int p = page != null ? page : 0;
        int s = size != null ? size : OperationFilter.DEFAULT_SIZE;
        validate(query, p, s);

        String number = Normalization.contractNumber(query);
        String inn = INN.matcher(query).matches() ? query : null;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("number", number)
                .addValue("inn", inn)
                .addValue("limit", s)
                .addValue("offset", (long) p * s);
        String matched = """
                SELECT c.id FROM contracts c WHERE c.contract_number_norm = :number
                UNION
                SELECT cp.contract_id FROM contract_parties cp JOIN clients cl ON cl.id = cp.client_id
                WHERE CAST(:inn AS VARCHAR) IS NOT NULL AND cl.inn = :inn
                """;

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + matched + ") m", params, Long.class);

        record Found(long id, String number, String type) {
        }
        List<Found> found = jdbc.query("""
                SELECT c.id, c.contract_number, c.contract_type
                FROM contracts c
                WHERE c.id IN (""" + matched + """
                )
                ORDER BY c.contract_number, c.id
                LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> new Found(rs.getLong("id"), rs.getString("contract_number"),
                rs.getString("contract_type")));

        var parties = partiesReader.partiesOf(found.stream().map(Found::id).toList());
        List<ContractRow> content = found.stream()
                .map(f -> new ContractRow(f.id(), f.number(), f.type(), parties.getOrDefault(f.id(), List.of())))
                .toList();
        return new PageResult<>(content, p, s, total == null ? 0 : total);
    }

    private static void validate(String query, int page, int size) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (query.isEmpty() || Normalization.contractNumber(query) == null) {
            errors.put("q", "enter a contract number or INN");
        } else if (query.length() > MAX_QUERY_LENGTH) {
            errors.put("q", "must be at most " + MAX_QUERY_LENGTH + " chars");
        }
        if (page < 0) {
            errors.put("page", "must be >= 0");
        }
        if (size < 1 || size > OperationFilter.MAX_SIZE) {
            errors.put("size", "must be between 1 and " + OperationFilter.MAX_SIZE);
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }
}
