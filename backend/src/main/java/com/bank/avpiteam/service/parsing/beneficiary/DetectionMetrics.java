package com.bank.avpiteam.service.parsing.beneficiary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class DetectionMetrics {

    private final MeterRegistry registry;
    private final Timer detectTimer;

    public DetectionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.detectTimer = Timer.builder("avpiteam.nlp.detect")
                .description("Время определения выгодоприобретателя (включая вызов NLP)")
                .publishPercentileHistogram()
                .register(registry);
    }

    /** Замеряет время и считает ошибки. Исключение пробрасывается дальше. */
    public <T> T time(Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return action.get();
        } catch (RuntimeException e) {
            registry.counter("avpiteam.nlp.detect.errors").increment();
            throw e;
        } finally {
            sample.stop(detectTimer);
        }
    }

    /** Счётчик результатов. Лейблы только с малой кардинальностью. */
    public void record(BeneficiaryDetectionResult result) {
        registry.counter("avpiteam.nlp.detect.results",
                        "method", result.method().name(),
                        "genuine_third_party", String.valueOf(result.isGenuineThirdParty()))
                .increment();
    }
}