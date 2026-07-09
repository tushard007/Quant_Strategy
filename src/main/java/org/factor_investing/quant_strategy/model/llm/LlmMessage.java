package org.factor_investing.quant_strategy.model.llm;

import jakarta.validation.constraints.NotBlank;

public class LlmMessage {
    @NotBlank
    private String role;

    @NotBlank
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
