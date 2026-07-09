package org.factor_investing.quant_strategy.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class IcaPromptRequest {
    @NotBlank
    @Schema(
            description = "ICA chat model id.",
            example = "gpt-4o",
            allowableValues = {
                    "advantage_assist.advantage-assist",
                    "gpt-4o",
                    "meta-llama/llama-4-maverick-17b-128e-instruct-fp8",
                    "claude-haiku-4-5",
                    "ibm/granite-4-h-small",
                    "claude-opus-4-6",
                    "gemma-4-26b-a4b-it",
                    "claude-sonnet-4-5",
                    "claude-sonnet-4-6",
                    "claude-opus-4-7",
                    "gpt-5.4-gus",
                    "gpt-5.1-chat-gus",
                    "gemini-3.1-pro-preview",
                    "claude-opus-4-8",
                    "gemini-3.5-flash"
            }
    )
    private String model;

    @NotBlank
    @Schema(description = "Prompt text to send as the user message.", example = "Explain Nifty 50 momentum investing in simple terms.")
    private String prompt;

    @Schema(description = "Optional system instruction.", example = "You are a helpful quant investing assistant.")
    private String systemPrompt;

    @Schema(description = "Enable server-sent-event streaming.", example = "false", defaultValue = "false")
    private Boolean stream = false;

    @Schema(description = "Sampling temperature. Leave empty unless the selected model supports it.", example = "1")
    private Double temperature;

    @JsonProperty("top_p")
    @Schema(description = "Nucleus sampling value. Leave empty unless the selected model supports it.", example = "1")
    private Double topP;

    @JsonProperty("max_tokens")
    @Schema(description = "Maximum generated tokens.", example = "500")
    private Integer maxTokens;

    @Valid
    @Schema(description = "Optional file or collection references for grounded completion.")
    private List<LlmFileRef> files;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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

    public List<LlmFileRef> getFiles() {
        return files;
    }

    public void setFiles(List<LlmFileRef> files) {
        this.files = files;
    }
}
