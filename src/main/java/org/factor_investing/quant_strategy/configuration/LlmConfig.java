package org.factor_investing.quant_strategy.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LlmProviderProperties.class)
public class LlmConfig {
    @Bean
    public RestClient llmRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
