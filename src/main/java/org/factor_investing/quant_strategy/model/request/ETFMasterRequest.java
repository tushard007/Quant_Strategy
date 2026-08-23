package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ETFMasterRequest(
        @NotBlank @Size(max = 50) String symbol,
        @NotBlank @Size(max = 255) String underlying,
        @NotBlank @Size(max = 255) String securityName,
        @NotBlank @Size(max = 30) String dateOfListing,
        @NotNull @Positive Integer marketLot,
        @NotBlank @Size(max = 20) String isinNumber,
        @NotNull @PositiveOrZero Double faceValue
) {
}
