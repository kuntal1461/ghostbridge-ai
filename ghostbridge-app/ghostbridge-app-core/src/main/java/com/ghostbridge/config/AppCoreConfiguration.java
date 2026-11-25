package com.ghostbridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the App core configuration properties. Beans of the use cases are discovered by the default scan. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppAiProperties.class)
public class AppCoreConfiguration {
}
