package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockMasterRequest(
        @NotBlank @Size(max = 30) String symbol,
        @NotBlank @Size(max = 255) String nameOfCompany,
        @NotBlank @Size(max = 20) String series,
        @NotBlank @Size(max = 20) String isinNumber,
        @NotBlank @Size(max = 150) String industry
) {
}
