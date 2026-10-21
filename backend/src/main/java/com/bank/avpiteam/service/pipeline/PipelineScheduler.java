package com.bank.avpiteam.service.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Фоновая обработка новых операций. Включается pipeline.scheduler.enabled=true.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pipeline.scheduler", name = "enabled", havingValue = "true")
public class PipelineScheduler {

    private final PipelineRunner runner;

    @Scheduled(fixedDelayString = "${pipeline.scheduler.fixed-delay-ms:5000}")
    public void tick() {
        runner.runUntilDrained(Duration.ofMinutes(5));
    }
}
