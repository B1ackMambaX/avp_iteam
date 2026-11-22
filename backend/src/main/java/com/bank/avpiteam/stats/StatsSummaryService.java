package com.bank.avpiteam.stats;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Бизнес-дашборд: что произошло с операциями, загруженными за день.
 * Сопоставление статусов с корзинами - docs/analysis/stats-status-mapping.md
 */
@Service
public class StatsSummaryService {

    // Одна выборка, обе части - index-only scan по индексам из V11:
    //  d - операции дня по created_at в полуинтервале [from, to);
    //  b - весь бэклог ручного разбора, независимо от дня.
    // Корзины определяются статусом (CHECK из V12 - ровно 8 значений), поэтому не пересекаются
    // и в сумме дают loaded. Ручное подтверждение - MANUAL_MAPPED, ручное отклонение - NOT_FOUND.
    private static final String SUMMARY_SQL = """
            SELECT d.loaded, d.in_progress, d.auto_mapped, d.manually_mapped,
                   d.ambiguous, d.manual_required, d.not_found, b.backlog
            FROM (
                SELECT count(*) AS loaded,
                       count(*) FILTER (WHERE status IN ('PENDING', 'WAITING_NLP', 'PARSED')) AS in_progress,
                       count(*) FILTER (WHERE status = 'AUTO_MAPPED') AS auto_mapped,
                       count(*) FILTER (WHERE status = 'MANUAL_MAPPED') AS manually_mapped,
                       count(*) FILTER (WHERE status = 'AMBIGUOUS') AS ambiguous,
                       count(*) FILTER (WHERE status = 'MANUAL_REQUIRED') AS manual_required,
                       count(*) FILTER (WHERE status = 'NOT_FOUND') AS not_found
                FROM operations
                WHERE created_at >= :from AND created_at < :to
            ) d
            CROSS JOIN (
                SELECT count(*) AS backlog
                FROM operations
                WHERE status IN ('AMBIGUOUS', 'MANUAL_REQUIRED')
            ) b
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final StatsProperties properties;
    private final boolean demo;
    private final Clock clock;
    private final Map<LocalDate, CachedSummary> cache = new ConcurrentHashMap<>();

    private record CachedSummary(StatsSummary value, long expiresAtMillis) {
    }

    @Autowired
    public StatsSummaryService(NamedParameterJdbcTemplate jdbc, StatsProperties properties, Environment environment) {
        this(jdbc, properties, environment, Clock.systemUTC());
    }

    StatsSummaryService(NamedParameterJdbcTemplate jdbc, StatsProperties properties,
                        Environment environment, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.demo = environment.acceptsProfiles(Profiles.of("demo"));
        this.clock = clock;
    }

    /** @param date день загрузки; null - сегодня в часовом поясе из конфига */
    public StatsSummary summary(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(properties.getTimeZone()));
        long now = clock.millis();
        CachedSummary cached = cache.get(day);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.value();
        }
        StatsSummary fresh = query(day);
        cache.put(day, new CachedSummary(fresh, now + properties.getCacheTtl().toMillis()));
        return fresh;
    }

    /**
     * Сброс кеша: по завершении pipeline/run и при /api/demo/reset.
     * После коммита (иначе параллельный запрос закеширует данные до сброса);
     * вне транзакции, как у конвейера, - сразу.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onOperationsChanged(OperationsChangedEvent event) {
        cache.clear();
    }

    private StatsSummary query(LocalDate day) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", day.atStartOfDay(properties.getTimeZone()).toOffsetDateTime())
                .addValue("to", day.plusDays(1).atStartOfDay(properties.getTimeZone()).toOffsetDateTime());
        return jdbc.queryForObject(SUMMARY_SQL, params, (rs, i) -> {
            long loaded = rs.getLong("loaded");
            long inProgress = rs.getLong("in_progress");
            long autoMapped = rs.getLong("auto_mapped");
            return new StatsSummary(
                    day,
                    demo,
                    loaded,
                    inProgress,
                    autoMapped,
                    rs.getLong("manually_mapped"),
                    new StatsSummary.AwaitingManual(rs.getLong("ambiguous"), rs.getLong("manual_required")),
                    rs.getLong("not_found"),
                    autoShare(autoMapped, loaded - inProgress),
                    rs.getLong("backlog"));
        });
    }

    // Доля автопривязки среди уже обработанных операций дня; при нуле в знаменателе - 0, а не NaN
    static double autoShare(long autoMapped, long processed) {
        if (processed <= 0) {
            return 0.0;
        }
        return Math.round(autoMapped * 10_000.0 / processed) / 10_000.0;
    }
}
