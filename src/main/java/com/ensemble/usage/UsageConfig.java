package com.ensemble.usage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link UsageProperties} as a Spring bean for {@link CallCapService}. */
@Configuration
@EnableConfigurationProperties(UsageProperties.class)
public class UsageConfig {
}
