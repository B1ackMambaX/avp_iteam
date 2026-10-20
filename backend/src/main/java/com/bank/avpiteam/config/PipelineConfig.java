package com.bank.avpiteam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MappingProperties.class, PipelineProperties.class})
public class PipelineConfig {
}
