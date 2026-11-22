package com.bank.avpiteam.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@EnableConfigurationProperties(StatsProperties.class)
public class StatsController {

    private final StatsSummaryService service;

    /** Бизнес-дашборд главной страницы (VIEW_DATA). date - день загрузки, по умолчанию сегодня. */
    @GetMapping("/summary")
    public StatsSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summary(date);
    }
}
