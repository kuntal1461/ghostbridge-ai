package com.ghostbridge.config;

import com.ghostbridge.util.UploadValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

/** Registers the shared non-web infrastructure. Activated through AutoConfiguration.imports, not scanning. */
@AutoConfiguration
@EnableConfigurationProperties(GhostBridgeProperties.class)
@PropertySource("classpath:ghostbridge-defaults.properties")
public class GhostBridgeCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GhostBridgeProductionValidator ghostBridgeProductionValidator(GhostBridgeProperties properties, Environment environment) {
        return new GhostBridgeProductionValidator(properties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    UploadValidator uploadValidator(GhostBridgeProperties properties) {
        return new UploadValidator(properties.upload());
    }
}
