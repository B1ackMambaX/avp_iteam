package com.bank.avpiteam.review;

import com.bank.avpiteam.domain.Operation.OperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Постраничная выборка операций и очереди ручного разбора (BE-1).
 * Фильтры, сортировка и пагинация - в БД; оценки кандидатов считаются тем же запросом для всей страницы.
 * Сортировка: дата операции по убыванию, затем id по убыванию - порядок стабилен при одинаковой дате.
 */
@Service
@RequiredArgsConstructor
public class OperationQueryService {

    public static final Set<OperationStatus> REVIEW_QUEUE =
            EnumSet.of(OperationStatus.AMBIGUOUS, OperationStatus.MANUAL_REQUIRED);

    private final NamedParameterJdbcTemplate jdbc;

    @PreAuthorize("hasAuthority('VIEW_DATA')")
    public PageResult<OperationRow> find(OperationFilter filter) {
        OperationFilter f = filter != null ? filter : OperationFilter.all();
        validate(f);
        int page = f.page() != null ? f.page() : 0;
        int size = f.size() != null ? f.size() : OperationFilter.DEFAULT_SIZE;

        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(f, params);

        Long total = jdbc.queryForObject("SELECT count(*) FROM operations o" + where, params, Long.class);

        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<OperationRow> content = jdbc.query("""
                WITH page AS (
                    SELECT o.id, o.operation_date, o.amount, o.currency_code, o.sender_name,
                           o.receiver_name, o.payment_purpose, o.status
                    FROM operations o
                """ + where + """
                    ORDER BY o.operation_date DESC, o.id DESC
                    LIMIT :limit OFFSET :offset
                ), scores AS (
                    SELECT operation_id, max(score) AS best_score, count(*) AS candidates
                    FROM operation_mapping_candidate
                    WHERE operation_id IN (SELECT id FROM page)
                    GROUP BY operation_id
                )
                SELECT p.*, s.best_score, coalesce(s.candidates, 0) AS candidates
                FROM page p LEFT JOIN scores s ON s.operation_id = p.id
                ORDER BY p.operation_date DESC, p.id DESC
                """, params, (rs, i) -> new OperationRow(
                rs.getLong("id"),
                rs.getTimestamp("operation_date").toLocalDateTime().toLocalDate(),
                rs.getTimestamp("operation_date").toLocalDateTime(),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                rs.getString("sender_name"),
                rs.getString("receiver_name"),
                rs.getString("payment_purpose"),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("best_score"),
                rs.getInt("candidates")));

        return new PageResult<>(content, page, size, total == null ? 0 : total);
    }

    /** Очередь ручного разбора: AMBIGUOUS и MANUAL_REQUIRED (или одна из них) в одной выборке. */
    @PreAuthorize("hasAuthority('VIEW_DATA')")
    public PageResult<OperationRow> reviewQueue(Set<OperationStatus> statuses, LocalDate from, LocalDate to,
                                                String q, Integer page, Integer size) {
        Set<OperationStatus> queue = statuses == null || statuses.isEmpty() ? REVIEW_QUEUE : statuses;
        if (!REVIEW_QUEUE.containsAll(queue)) {
            throw ValidationFailedException.of("status", "only AMBIGUOUS and MANUAL_REQUIRED are allowed");
        }
        return find(new OperationFilter(queue, from, to, q, page, size));
    }

    private static void validate(OperationFilter f) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (f.page() != null && f.page() < 0) {
            errors.put("page", "must be >= 0");
        }
        if (f.size() != null && (f.size() < 1 || f.size() > OperationFilter.MAX_SIZE)) {
            errors.put("size", "must be between 1 and " + OperationFilter.MAX_SIZE);
        }
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            errors.put("from", "must not be after 'to'");
        }
        if (f.q() != null && f.q().strip().length() > OperationFilter.MAX_QUERY_LENGTH) {
            errors.put("q", "must be at most " + OperationFilter.MAX_QUERY_LENGTH + " chars");
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    private static String where(OperationFilter f, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            conditions.add("o.status IN (:statuses)");
            params.addValue("statuses", f.statuses().stream().map(Enum::name).toList());
        }
        // Даты включительно: [from 00:00, to + 1 день 00:00)
        if (f.from() != null) {
            conditions.add("o.operation_date >= :from");
            params.addValue("from", f.from().atStartOfDay());
        }
        if (f.to() != null) {
            conditions.add("o.operation_date < :toExclusive");
            params.addValue("toExclusive", f.to().plusDays(1).atStartOfDay());
        }
        String q = f.q() == null ? "" : f.q().strip();
        if (!q.isEmpty()) {
            conditions.add("""
                    (o.payment_purpose ILIKE :like ESCAPE '\\' OR o.sender_name ILIKE :like ESCAPE '\\'
                     OR o.receiver_name ILIKE :like ESCAPE '\\'
                     OR o.sender_inn = :q OR o.receiver_inn = :q OR o.reference = :q)""");
            params.addValue("q", q).addValue("like", "%" + escapeLike(q) + "%");
        }
        return conditions.isEmpty() ? "\n" : "\nWHERE " + String.join("\n  AND ", conditions) + "\n";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
