package com.bank.avpiteam.service.pipeline;

import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class StageTimers {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    public void record(String stage, long nanos) {
        timer(stage).record(nanos, TimeUnit.NANOSECONDS);
    }
    public StageTimers(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String stage, Supplier<T> body) {
        return timer(stage).record(body);
    }

    public void run(String stage, Runnable body) {
        timer(stage).record(body);
    }

    private Timer timer(String stage) {
        return timers.computeIfAbsent(stage, s ->
                Timer.builder("avp_pipeline_stage_seconds")
                        .description("Время стадий конвейера")
                        .tag("stage", s)
                        .register(registry));
    }
}