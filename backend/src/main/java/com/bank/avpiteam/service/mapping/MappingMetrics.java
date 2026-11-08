package com.bank.avpiteam.service.mapping;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class MappingMetrics {

    private final MeterRegistry registry;
    private final Timer batchTimer;
    private final Counter processedCounter;

    public MappingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.batchTimer = Timer.builder("avpiteam.mapping.batch")
                .description("Время обработки одной пачки операций в маппинге")
                .publishPercentileHistogram()
                .register(registry);
        this.processedCounter = Counter.builder("avpiteam.mapping.processed")
                .description("Сколько операций прошло через маппинг")
                .register(registry);
    }

    /** Вызывать один раз по завершении пачки. Пустые пачки не записываем. */
    public void recordBatch(long processed, Map<?, ? extends Number> byStatus, long elapsedNanos) {
        if (processed <= 0) {
            return;
        }
        batchTimer.record(Duration.ofNanos(elapsedNanos));
        processedCounter.increment(processed);
        byStatus.forEach((status, count) ->
                registry.counter("avpiteam.mapping.results", "status", String.valueOf(status))
                        .increment(count.doubleValue()));
    }
}