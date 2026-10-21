package com.bank.avpiteam.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Демо-эндпоинты (профиль demo): точность маппинга по сценариям и сброс для повторного прогона.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final JdbcTemplate jdbc;

    private static final class ScenarioStats {
        int total;
        int correct;
        int wrongContract;
        final Map<String, Integer> actual = new TreeMap<>();
    }

    /**
     * Сравнивает результат маппинга с ожидаемым ответом, зашитым в reference:
     * DEMO-{сценарий}-{ожидаемый статус}-{ожидаемый договор}-{номер}.
     * wrongAutoMapped - привязки не к тому договору: самая дорогая ошибка, её должно быть 0.
     */
    @GetMapping("/report")
    public Map<String, Object> report() {
        Map<String, ScenarioStats> byScenario = new LinkedHashMap<>();
        int[] totals = new int[4]; // всего, верно, ошибочных автопривязок, ещё не обработано
        jdbc.query("""
                SELECT reference, status, contract_id FROM operations
                WHERE reference LIKE 'DEMO-%'
                ORDER BY id
                """, rs -> {
            String[] parts = rs.getString("reference").split("-");
            String scenario = parts[1];
            String expectedStatus = parts[2];
            long expectedContract = Long.parseLong(parts[3]);
            String status = rs.getString("status");
            Long contractId = rs.getObject("contract_id") == null ? null : rs.getLong("contract_id");

            ScenarioStats stats = byScenario.computeIfAbsent(scenario, k -> new ScenarioStats());
            stats.total++;
            stats.actual.merge(status, 1, Integer::sum);
            totals[0]++;

            boolean wrongAuto = "AUTO_MAPPED".equals(status)
                    && (!"AUTO_MAPPED".equals(expectedStatus) || contractId == null || contractId != expectedContract);
            boolean correct = status.equals(expectedStatus) && !wrongAuto;
            if (correct) {
                stats.correct++;
                totals[1]++;
            }
            if (wrongAuto) {
                stats.wrongContract++;
                totals[2]++;
            }
            if (status.equals("PENDING") || status.equals("WAITING_NLP") || status.equals("PARSED")) {
                totals[3]++;
            }
        });

        Map<String, Object> scenarios = new LinkedHashMap<>();
        byScenario.forEach((name, s) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("total", s.total);
            row.put("correct", s.correct);
            row.put("accuracy", percent(s.correct, s.total));
            row.put("wrongAutoMapped", s.wrongContract);
            row.put("actualStatuses", s.actual);
            scenarios.put(name, row);
        });

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", totals[0]);
        report.put("correct", totals[1]);
        report.put("accuracy", percent(totals[1], totals[0]));
        report.put("wrongAutoMapped", totals[2]);
        report.put("notProcessedYet", totals[3]);
        report.put("byScenario", scenarios);
        return report;
    }

    /** Возвращает демо-операции в PENDING и удаляет результаты разбора и маппинга - для повторной записи демо. */
    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        String demoOperations = "SELECT id FROM operations WHERE reference LIKE 'DEMO-%'";
        int candidates = jdbc.update("DELETE FROM operation_mapping_candidate WHERE operation_id IN (" + demoOperations + ")");
        int parses = jdbc.update("DELETE FROM operation_parse_result WHERE operation_id IN (" + demoOperations + ")");
        int tasks = jdbc.update("DELETE FROM nlp_task WHERE operation_id IN (" + demoOperations + ")");
        int operations = jdbc.update("""
                UPDATE operations
                SET status = 'PENDING', contract_id = NULL, mapping_reason = NULL,
                    mapped_by = NULL, mapped_at = NULL, updated_at = now()
                WHERE reference LIKE 'DEMO-%'
                """);
        return Map.of("operations", operations, "candidatesDeleted", candidates,
                "parseResultsDeleted", parses, "nlpTasksDeleted", tasks);
    }

    private static double percent(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
    }
}
