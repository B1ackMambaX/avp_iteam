package com.bank.avpiteam.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoConfig {
}
