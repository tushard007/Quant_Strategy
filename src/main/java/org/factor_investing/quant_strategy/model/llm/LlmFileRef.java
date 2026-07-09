package org.factor_investing.quant_strategy.model.llm;

import jakarta.validation.constraints.NotBlank;

public class LlmFileRef {
    @NotBlank
    private String type;

    @NotBlank
    private String id;

    private String name;
    private String context;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
