package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record BreadthScoreConfigurationResponse(UUID id,
                                                int version,
                                                Map<String, Integer> weights,
                                                double greenThreshold,
                                                double amberThreshold,
                                                double vixSpikePercent,
                                                int risingFallingLookbackSessions,
                                                int expansionContractionLookbackSessions,
                                                LocalDate effectiveFrom,
                                                boolean active) {
    public static BreadthScoreConfigurationResponse from(BreadthScoreConfiguration configuration) {
        return new BreadthScoreConfigurationResponse(configuration.getId(), configuration.getVersion(),
                configuration.getWeights(), configuration.getGreenThreshold(), configuration.getAmberThreshold(),
                configuration.getVixSpikePercent(), configuration.getRisingFallingLookbackSessions(),
                configuration.getExpansionContractionLookbackSessions(), configuration.getEffectiveFrom(),
                configuration.isActive());
    }
}
