package org.factor_investing.quant_strategy.model.llm;

import java.util.Map;

public class LlmCompletionResponse {
    private LlmProvider provider;
    private String model;
    private String content;
    private Map<String, Object> usage;
    private Map<String, Object> rawResponse;

    public LlmProvider getProvider() {
        return provider;
    }

    public void setProvider(LlmProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getUsage() {
        return usage;
    }

    public void setUsage(Map<String, Object> usage) {
        this.usage = usage;
    }

    public Map<String, Object> getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(Map<String, Object> rawResponse) {
        this.rawResponse = rawResponse;
    }
}
