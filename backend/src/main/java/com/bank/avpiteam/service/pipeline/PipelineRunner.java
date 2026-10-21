package com.bank.avpiteam.service.pipeline;

import com.bank.avpiteam.config.PipelineProperties;
import com.bank.avpiteam.service.mapping.MappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Прогоняет конвейер до конца: приём новых операций -> разбор назначения -> маппинг.
 * Безопасен для параллельного запуска (REST + планировщик, несколько инстансов):
 * все шаги забирают строки через FOR UPDATE SKIP LOCKED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineRunner {

    private final IntakeService intakeService;
    private final NlpTaskWorker nlpTaskWorker;
    private final MappingService mappingService;
    private final PipelineProperties properties;
    private final JdbcTemplate jdbc;

    public record RunReport(
            String parser,
            long millis,
            int intake,
            int nlpDone,
            int nlpRetried,
            int nlpFailed,
            int mapped,
            long mappingMillis,
            double mappedPerSecond,
            Map<String, Integer> mappedByStatus,
            boolean timedOut,
            Map<String, Object> stats
    ) {
    }

    /** Работает, пока есть что обрабатывать, но не дольше maxDuration. */
    public RunReport runUntilDrained(Duration maxDuration) {
        long started = System.nanoTime();
        long deadline = started + maxDuration.toNanos();
        int intake = 0;
        int mapped = 0;
        long mappingMillis = 0;
        int nlpDone = 0;
        int nlpRetried = 0;
        int nlpFailed = 0;
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        boolean timedOut = false;

        while (true) {
            if (System.nanoTime() > deadline) {
                timedOut = true;
                break;
            }
            int accepted = intakeService.intakeBatch();
            intake += accepted;

            NlpTaskWorker.BatchResult nlp = NlpTaskWorker.BatchResult.EMPTY;
            if (properties.getParser() == PipelineProperties.ParserMode.NLP) {
                nlp = nlpTaskWorker.processBatch();
                nlpDone += nlp.done();
                nlpRetried += nlp.retried();
                nlpFailed += nlp.failed();
            }

            MappingService.BatchResult batch = mappingService.mapBatch();
            mapped += batch.processed();
            mappingMillis += batch.millis();
            batch.byStatus().forEach((status, count) -> byStatus.merge(status, count, Integer::sum));

            if (accepted == 0 && nlp.claimed() == 0 && batch.processed() == 0) {
                // Задачи, отложенные на повтор (next_try_at в будущем), подберёт следующий запуск
                break;
            }
        }

        long millis = (System.nanoTime() - started) / 1_000_000;
        double perSecond = mappingMillis == 0 ? 0 : mapped * 1000.0 / mappingMillis;
        RunReport report = new RunReport(properties.getParser().name(), millis, intake,
                nlpDone, nlpRetried, nlpFailed, mapped, mappingMillis, Math.round(perSecond * 10) / 10.0,
                byStatus, timedOut, stats());
        if (mapped > 0 || intake > 0) {
            log.info("Конвейер ({}): принято {}, NLP {}/{}/{} (готово/повтор/ошибка), смаплено {} за {} мс ({} оп/с), статусы {}",
                    report.parser(), intake, nlpDone, nlpRetried, nlpFailed, mapped, mappingMillis,
                    report.mappedPerSecond(), byStatus);
        }
        return report;
    }

    /** Сводка по операциям и очереди NLP. */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("operationsByStatus", countBy("SELECT status, count(*) FROM operations GROUP BY status ORDER BY status"));
        stats.put("operationsByReason", countBy("""
                SELECT mapping_reason, count(*) FROM operations
                WHERE mapping_reason IS NOT NULL GROUP BY mapping_reason ORDER BY mapping_reason
                """));
        stats.put("nlpTasksByStatus", countBy("SELECT status, count(*) FROM nlp_task GROUP BY status ORDER BY status"));
        stats.put("contracts", jdbc.queryForObject("SELECT count(*) FROM contracts", Long.class));
        stats.put("clients", jdbc.queryForObject("SELECT count(*) FROM clients", Long.class));
        return stats;
    }

    private Map<String, Long> countBy(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }
}
