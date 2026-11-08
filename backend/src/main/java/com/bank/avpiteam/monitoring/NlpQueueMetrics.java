package com.bank.avpiteam.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NlpQueueMetrics {

    private static final Logger log = LoggerFactory.getLogger(NlpQueueMetrics.class);
    private static final List<String> STATUSES = List.of("NEW", "IN_PROGRESS", "FAILED");
    private static final long TTL_MS = 5_000;

    private final JdbcTemplate jdbc;
    private volatile Map<String, Long> snapshot = Map.of();
    private volatile long loadedAt = 0;

    public NlpQueueMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (String status : STATUSES) {
            Gauge.builder("avpiteam.nlp.queue.size", () -> valueOf(status))
                    .tag("status", status)
                    .description("Количество задач в очереди nlp_task по статусу")
                    .register(registry);
        }
    }

    private double valueOf(String status) {
        refreshIfStale();
        return snapshot.getOrDefault(status, 0L);
    }

    private synchronized void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < TTL_MS) {
            return;
        }
        loadedAt = now; // ставим сразу: при сбое БД не долбим её на каждом скрейпе
        try {
            Map<String, Long> fresh = new HashMap<>();
            jdbc.query(
                    "SELECT status, count(*) AS cnt FROM nlp_task " +
                            "WHERE status IN ('NEW','IN_PROGRESS','FAILED') GROUP BY status",
                    (RowCallbackHandler) rs -> fresh.put(rs.getString("status"), rs.getLong("cnt")));
            snapshot = fresh;
        } catch (DataAccessException e) {
            log.warn("Не удалось обновить метрику очереди nlp_task: {}", e.getMessage());
        }
    }
}