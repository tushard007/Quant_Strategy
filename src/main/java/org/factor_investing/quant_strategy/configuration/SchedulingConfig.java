package org.factor_investing.quant_strategy.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
