package org.factor_investing.quant_strategy.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class LlmCompletionRequest {
    private LlmProvider provider;

    @NotBlank
    private String model;

    private String namespace;
    private String systemPrompt;
    private String prompt;

    @Valid
    private List<LlmMessage> messages;

    @Valid
    private List<LlmFileRef> files;

    private Boolean stream;
    private Double temperature;
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("max_tokens")
    private Integer maxTokens;

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<LlmMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<LlmMessage> messages) {
        this.messages = messages;
    }

    public List<LlmFileRef> getFiles() {
        return files;
    }

    public void setFiles(List<LlmFileRef> files) {
        this.files = files;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
