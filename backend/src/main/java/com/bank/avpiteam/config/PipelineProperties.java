package com.bank.avpiteam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры конвейера обработки операций:
 * PENDING -> (разбор назначения) -> PARSED -> (маппинг) -> итоговый статус.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pipeline")
public class PipelineProperties {

    /**
     * NLP    - разбор через очередь nlp_task и Python-сервис (BeneficiaryDetectionService);
     * MARKER - разбор в процессе regex-парсером, без Python. Запасной вариант,
     *          если NLP-сервис недоступен (демо, локальная отладка).
     */
    private ParserMode parser = ParserMode.NLP;

    // Сколько PENDING-операций за раз ставится в очередь (или разбирается маркерным парсером)
    private int intakeBatchSize = 1000;

    private String markerParserVersion = "regex-v1";

    private Nlp nlp = new Nlp();

    private Scheduler scheduler = new Scheduler();

    public enum ParserMode {
        NLP,
        MARKER
    }

    @Getter
    @Setter
    public static class Nlp {
        // Сколько задач воркер забирает за раз
        private int batchSize = 50;
        // Сколько запросов к NLP-сервису идёт одновременно
        private int parallelism = 8;
        // После стольких неудачных попыток задача FAILED, операция идёт в маппинг без разбора
        private int maxAttempts = 3;
        // Пока задача IN_PROGRESS, другие воркеры её не трогают; после - считается зависшей
        private int leaseSeconds = 120;
        // Пауза перед повтором растёт с номером попытки: retryDelaySeconds * attempts
        private int retryDelaySeconds = 30;
        private String parserVersion = "semantic-v1";
    }

    @Getter
    @Setter
    public static class Scheduler {
        // Фоновая обработка новых операций. По умолчанию выключена -
        // конвейер запускается вручную через POST /api/pipeline/run
        private boolean enabled = false;
        private long fixedDelayMs = 5000;
    }
}
