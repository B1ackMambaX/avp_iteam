package com.bank.avpiteam.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    // Объём справочника. Для прогона "как у заказчика": contracts = 10_000_000, clients = 3_000_000
    private int clients = 300_000;
    private int contracts = 1_000_000;

    // Операций за "день"
    private int operations = 10_000;

    private long seed = 42;
}
