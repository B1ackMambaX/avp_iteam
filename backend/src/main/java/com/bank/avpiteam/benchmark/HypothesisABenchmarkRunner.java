package com.bank.avpiteam.benchmark;

import com.bank.avpiteam.service.parsing.ParsedPaymentPurpose;
import com.bank.avpiteam.service.parsing.PaymentPurposeParser;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Запускается профилем benchmark-a.
 * Делает две вещи:
 *  1) Считает точность парсера гипотезы А на размеченном датасете
 *  2) Проводит нагрузочный прогон и снимает CPU/RAM через JMX
 */
@Slf4j
@Component
@Profile("benchmark-a")
@RequiredArgsConstructor
public class HypothesisABenchmarkRunner implements CommandLineRunner {

    private final PaymentPurposeParser parser; // внедрится RegexPaymentPurposeParser

    @Value("${benchmark.dataset-path}")
    private String datasetPath;

    @Value("${benchmark.performance-iterations:200000}")
    private int performanceIterations;

    @Override
    public void run(String... args) throws Exception {
        List<TestDatasetRow> dataset = TestDatasetLoader.load(Path.of(datasetPath));
        log.info("Загружено {} строк размеченного датасета", dataset.size());

        runAccuracyPhase(dataset);
        runPerformancePhase(dataset);
    }

    private void runAccuracyPhase(List<TestDatasetRow> dataset) {
        log.info("========== ФАЗА 1: ТОЧНОСТЬ (Гипотеза А (алгоритмический)) ==========");

        int total = dataset.size();
        int correctClassification = 0;

        int truePositive = 0;  // верно определили третье лицо
        int falsePositive = 0; // определили третье лицо, а его не было
        int falseNegative = 0; // пропустили платёж за третье лицо
        int trueNegative = 0;  // верно определили, что не третье лицо

        int thirdPartyRows = 0;
        int correctNameExtraction = 0;

        for (TestDatasetRow row : dataset) {
            ParsedPaymentPurpose result = parser.parse(row.paymentPurpose());

            boolean expected = row.expectedThirdParty();
            boolean actual = result.thirdParty();

            if (expected == actual) {
                correctClassification++;
            }

            if (expected && actual) truePositive++;
            if (!expected && actual) falsePositive++;
            if (expected && !actual) falseNegative++;
            if (!expected && !actual) trueNegative++;

            if (expected) {
                thirdPartyRows++;
                if (namesMatch(row.expectedBeneficiaryName(), result.beneficiaryName())) {
                    correctNameExtraction++;
                }
            }

            log.debug("id={} | expected_tp={} actual_tp={} | expected_name='{}' actual_name='{}'",
                    row.id(), expected, actual,
                    row.expectedBeneficiaryName(), result.beneficiaryName());
        }

        double classificationAccuracy = 100.0 * correctClassification / total;
        double nameExtractionAccuracy = thirdPartyRows == 0
                ? 0.0
                : 100.0 * correctNameExtraction / thirdPartyRows;

        log.info("Всего строк: {}", total);
        log.info("Точность классификации 'третье лицо / не третье лицо': {}%",
                String.format(Locale.US, "%.1f", classificationAccuracy));
        log.info("  TP={}, FP={}, FN={}, TN={}",
                truePositive, falsePositive, falseNegative, trueNegative);
        log.info("Строк с третьим лицом: {}", thirdPartyRows);
        log.info("Точность извлечения ФИО выгодоприобретателя (из строк с третьим лицом): {}%",
                String.format(Locale.US, "%.1f", nameExtractionAccuracy));
    }

    private boolean namesMatch(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return normalize(expected).equals(normalize(actual));
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.of("ru")).replaceAll("\\s+", " ").trim();
    }

    private void runPerformancePhase(List<TestDatasetRow> dataset) throws InterruptedException {
        log.info("========== ФАЗА 2: НАГРУЗКА (Гипотеза А (алгоритмический)) ==========");
        log.info("Итераций: {}", performanceIterations);
        log.info("Если необходимо снять метрики — есть 10 секунд," +
                        " чтобы подключиться к процессу (jps/VisualVM -> этот PID: {})",
                ProcessHandle.current().pid());

        Thread.sleep(10_000);

        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // просим GC прибраться перед замером, чтобы снизить шум
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        long cpuTimeBefore = osBean.getProcessCpuTime(); // наносекунды CPU-времени процесса
        long wallTimeBeforeNs = System.nanoTime();

        // Собственно нагрузка - гоняем парсер по кругу на всём датасете
        int datasetSize = dataset.size();
        for (int i = 0; i < performanceIterations; i++) {
            TestDatasetRow row = dataset.get(i % datasetSize);
            parser.parse(row.paymentPurpose());
        }

        long wallTimeAfterNs = System.nanoTime();
        long cpuTimeAfter = osBean.getProcessCpuTime();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();

        double wallTimeMs = (wallTimeAfterNs - wallTimeBeforeNs) / 1_000_000.0;
        double cpuTimeMs = (cpuTimeAfter - cpuTimeBefore) / 1_000_000.0;
        int cores = osBean.getAvailableProcessors();
        double cpuUtilizationPercent = (cpuTimeMs / (wallTimeMs * cores)) * 100.0;

        long memDeltaBytes = memAfter - memBefore;

        log.info("Время выполнения: {} мс", String.format(Locale.US, "%.1f", wallTimeMs));
        log.info("Throughput: {} операций/сек",
                String.format(Locale.US, "%.0f", performanceIterations / (wallTimeMs / 1000.0)));
        log.info("CPU-время процесса потрачено: {} мс", String.format(Locale.US, "%.1f", cpuTimeMs));
        log.info("Примерная загрузка CPU за время теста: {}% (ядер доступно: {})",
                String.format(Locale.US, "%.1f", cpuUtilizationPercent), cores);
        log.info("Изменение используемой памяти heap (грубая оценка): {} МБ",
                String.format(Locale.US, "%.2f", memDeltaBytes / 1024.0 / 1024.0));
        log.info("ВАЖНО: цифры CPU/RAM из JMX приблизительные (мешают JIT-разогрев и GC)." +
                " Для точных графиков сверяемся с VisualVM.");
    }
}