package com.bank.avpiteam.stats;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Счётчики дашборда на настоящем PostgreSQL со схемой из миграций Flyway.
 * Без Docker тест пропускается. Образ можно переопределить: AVP_TEST_PG_IMAGE=postgres:16.
 */
@Testcontainers(disabledWithoutDocker = true)
class StatsSummaryServiceTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse(System.getenv().getOrDefault("AVP_TEST_PG_IMAGE", "postgres:16-alpine"))
                    .asCompatibleSubstituteFor("postgres"));

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final AtomicInteger SEQ = new AtomicInteger();

    static NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(ds);
    }

    @BeforeEach
    void clean() {
        jdbc.getJdbcTemplate().execute("TRUNCATE operations CASCADE");
    }

    private StatsSummaryService service(Clock clock) {
        StatsProperties properties = new StatsProperties();
        properties.setTimeZone(MSK);
        return new StatsSummaryService(jdbc, properties, new MockEnvironment(), clock);
    }

    private StatsSummaryService service() {
        // "сейчас" - середина DAY по Москве
        return service(Clock.fixed(DAY.atTime(12, 0).atZone(MSK).toInstant(), ZoneOffset.UTC));
    }

    private static void insert(String status, String mappingSource, OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO operations (reference, operation_date, amount, amount_rub, status,
                                        mapping_source, created_at)
                VALUES (:ref, now(), 1, 1, :status, :source, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("ref", "T-" + SEQ.incrementAndGet())
                .addValue("status", status)
                .addValue("source", mappingSource)
                .addValue("createdAt", createdAt));
    }

    private static OffsetDateTime at(LocalDate day, int hour, int minute, int second) {
        return day.atTime(hour, minute, second).atZone(MSK).toOffsetDateTime();
    }

    private static long bucketsSum(StatsSummary s) {
        return s.inProgress() + s.autoMapped() + s.manuallyMapped()
                + s.awaitingManual().ambiguous() + s.awaitingManual().manualRequired() + s.notFound();
    }

    @Test
    void emptyDayIsAllZeros() {
        StatsSummary s = service().summary(DAY);

        assertThat(s.date()).isEqualTo(DAY);
        assertThat(s.demo()).isFalse();
        assertThat(s.loaded()).isZero();
        assertThat(bucketsSum(s)).isZero();
        assertThat(s.autoShare()).isEqualTo(0.0);
        assertThat(s.backlogAwaitingManual()).isZero();
    }

    @Test
    void autoShareIsZeroWhenNothingProcessedYet() {
        // loaded - inProgress = 0: делить не на что
        insert("PENDING", null, at(DAY, 10, 0, 0));
        insert("WAITING_NLP", null, at(DAY, 10, 0, 0));
        insert("PARSED", null, at(DAY, 10, 0, 0));

        StatsSummary s = service().summary(DAY);

        assertThat(s.loaded()).isEqualTo(3);
        assertThat(s.inProgress()).isEqualTo(3);
        assertThat(s.autoShare()).isEqualTo(0.0);
        assertThat(Double.isNaN(s.autoShare())).isFalse();
    }

    @Test
    void dayIsHalfOpenIntervalInConfiguredZone() {
        LocalDate previous = DAY.minusDays(1);
        insert("AUTO_MAPPED", "AUTO", at(previous, 23, 59, 59)); // ещё предыдущий день
        insert("AUTO_MAPPED", "AUTO", at(DAY, 0, 0, 0));         // уже DAY
        insert("NOT_FOUND", null, at(DAY, 23, 59, 59));          // ещё DAY
        insert("NOT_FOUND", null, at(DAY.plusDays(1), 0, 0, 0)); // уже следующий

        StatsSummaryService service = service();

        assertThat(service.summary(previous).loaded()).isEqualTo(1);
        StatsSummary day = service.summary(DAY);
        assertThat(day.loaded()).isEqualTo(2);
        assertThat(day.autoMapped()).isEqualTo(1);
        assertThat(day.notFound()).isEqualTo(1);
        assertThat(service.summary(DAY.plusDays(1)).loaded()).isEqualTo(1);
    }

    @Test
    void dayBoundaryDoesNotDependOnSessionTimeZone() {
        // 00:30 по Москве - это 21:30 UTC предыдущего дня; операция всё равно относится к DAY
        insert("AUTO_MAPPED", "AUTO", at(DAY, 0, 30, 0));

        assertThat(service().summary(DAY).loaded()).isEqualTo(1);
        assertThat(service().summary(DAY.minusDays(1)).loaded()).isZero();
    }

    @Test
    void bucketsSumToLoaded() {
        insert("PENDING", null, at(DAY, 9, 0, 0));
        insert("WAITING_NLP", null, at(DAY, 9, 0, 0));
        insert("PARSED", null, at(DAY, 9, 0, 0));
        insert("AUTO_MAPPED", "AUTO", at(DAY, 9, 0, 0));
        insert("AUTO_MAPPED", "AUTO", at(DAY, 9, 0, 0));
        insert("MANUAL_MAPPED", "MANUAL", at(DAY, 9, 0, 0));  // подтверждено оператором
        insert("AMBIGUOUS", null, at(DAY, 9, 0, 0));
        insert("MANUAL_REQUIRED", null, at(DAY, 9, 0, 0));
        insert("NOT_FOUND", null, at(DAY, 9, 0, 0));
        insert("NOT_FOUND", "MANUAL", at(DAY, 9, 0, 0));      // отклонено оператором

        StatsSummary s = service().summary(DAY);

        assertThat(s.loaded()).isEqualTo(10);
        assertThat(s.inProgress()).isEqualTo(3);
        assertThat(s.autoMapped()).isEqualTo(2);
        assertThat(s.manuallyMapped()).isEqualTo(1);
        assertThat(s.awaitingManual().ambiguous()).isEqualTo(1);
        assertThat(s.awaitingManual().manualRequired()).isEqualTo(1);
        assertThat(s.notFound()).as("ручное отклонение - тоже «договор не найден»").isEqualTo(2);
        // Корзины - по статусу, 8 значений CHECK: каждая операция ровно в одной, сумма равна loaded
        assertThat(bucketsSum(s)).isEqualTo(s.loaded());
        assertThat(s.autoShare()).isEqualTo(Math.round(2 * 10_000.0 / 7) / 10_000.0);
    }

    @Test
    void backlogCountsAllDays() {
        insert("AMBIGUOUS", null, at(DAY.minusDays(5), 12, 0, 0));
        insert("MANUAL_REQUIRED", null, at(DAY.minusDays(1), 12, 0, 0));
        insert("AMBIGUOUS", null, at(DAY, 12, 0, 0));
        insert("MANUAL_MAPPED", "MANUAL", at(DAY.minusDays(2), 12, 0, 0)); // уже разобрана вручную

        StatsSummary s = service().summary(DAY);

        assertThat(s.loaded()).isEqualTo(1);
        assertThat(s.backlogAwaitingManual()).isEqualTo(3);
    }

    @Test
    void dateDefaultsToTodayInConfiguredZone() {
        // 2026-11-09 22:30 UTC = 2026-11-10 01:30 по Москве
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T22:30:00Z"), ZoneOffset.UTC);

        assertThat(service(clock).summary(null).date()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void cachedForTtlAndEvictedOnOperationsChanged() {
        StatsSummaryService service = service();
        insert("NOT_FOUND", null, at(DAY, 9, 0, 0));
        assertThat(service.summary(DAY).loaded()).isEqualTo(1);

        insert("NOT_FOUND", null, at(DAY, 9, 0, 0));
        assertThat(service.summary(DAY).loaded()).as("в пределах TTL - из кеша").isEqualTo(1);

        service.onOperationsChanged(new OperationsChangedEvent("test"));
        assertThat(service.summary(DAY).loaded()).isEqualTo(2);
    }

    @Test
    void cacheExpiresAfterTtl() {
        MutableClock clock = new MutableClock(DAY.atTime(12, 0).atZone(MSK).toInstant());
        StatsSummaryService service = service(clock);
        insert("NOT_FOUND", null, at(DAY, 9, 0, 0));
        assertThat(service.summary(DAY).loaded()).isEqualTo(1);

        insert("NOT_FOUND", null, at(DAY, 9, 0, 0));
        clock.advanceSeconds(31);
        assertThat(service.summary(DAY).loaded()).isEqualTo(2);
    }

    @Test
    void demoFlagFollowsProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        StatsProperties properties = new StatsProperties();
        properties.setTimeZone(MSK);
        StatsSummaryService service = new StatsSummaryService(jdbc, properties, env, Clock.systemUTC());

        assertThat(service.summary(DAY).demo()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
