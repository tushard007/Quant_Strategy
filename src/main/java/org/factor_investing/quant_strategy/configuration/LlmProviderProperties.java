package org.factor_investing.quant_strategy.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProviderProperties {
    private Provider ica = new Provider();
    private Provider openai = new Provider();
    private Provider anthropic = new Provider();
    private Provider gemini = new Provider();

    public Provider getIca() {
        return ica;
    }

    public void setIca(Provider ica) {
        this.ica = ica;
    }

    public Provider getOpenai() {
        return openai;
    }

    public void setOpenai(Provider openai) {
        this.openai = openai;
    }

    public Provider getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(Provider anthropic) {
        this.anthropic = anthropic;
    }

    public Provider getGemini() {
        return gemini;
    }

    public void setGemini(Provider gemini) {
        this.gemini = gemini;
    }

    public static class Provider {
        private String apiKey;
        private String baseUrl;
        private String apiVersion;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }
    }
}
