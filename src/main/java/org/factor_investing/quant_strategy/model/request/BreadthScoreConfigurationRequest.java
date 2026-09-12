package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.Map;

public record BreadthScoreConfigurationRequest(
        @NotEmpty Map<String, @NotNull @PositiveOrZero Integer> weights,
        @DecimalMin("0") @DecimalMax("100") double greenThreshold,
        @DecimalMin("0") @DecimalMax("100") double amberThreshold,
        @PositiveOrZero double vixSpikePercent,
        @Positive int risingFallingLookbackSessions,
        @Positive int expansionContractionLookbackSessions,
        LocalDate effectiveFrom) {

    @AssertTrue(message = "amberThreshold must be lower than greenThreshold")
    public boolean isThresholdOrderValid() {
        return amberThreshold < greenThreshold;
    }

    @AssertTrue(message = "weights must total 100")
    public boolean isWeightTotalValid() {
        return weights == null || weights.values().stream().filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum() == 100;
    }
}
