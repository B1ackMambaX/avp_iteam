package com.bank.avpiteam.stats;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@Getter
@Setter
@ConfigurationProperties(prefix = "stats")
public class StatsProperties {

    // Часовой пояс операционного дня. Берётся из конфига, а не из системы,
    // чтобы граница дня не зависела от машины, где запущен сервис
    private ZoneId timeZone = ZoneId.of("Europe/Moscow");

    // Сколько живёт закешированный ответ по дате
    private Duration cacheTtl = Duration.ofSeconds(30);
}
