package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IndexMasterRequest(
        @NotBlank @Size(max = 100) String symbol,
        @NotBlank @Size(max = 255) String indexName,
        @NotBlank @Size(max = 255) String instrumentKey
) {
}
