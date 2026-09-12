package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public record BreadthScoreParameters(int version,
                                     Map<String, Integer> weights,
                                     double greenThreshold,
                                     double amberThreshold,
                                     double vixSpikePercent,
                                     int trendLookback,
                                     int expansionLookback) {
    public BreadthScoreParameters {
        weights = Map.copyOf(weights);
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight != 100) throw new IllegalArgumentException("Breadth score weights must total 100");
        if (amberThreshold < 0 || greenThreshold > 100 || amberThreshold >= greenThreshold) {
            throw new IllegalArgumentException("Breadth score thresholds are invalid");
        }
        if (trendLookback < 1 || expansionLookback < 1 || vixSpikePercent < 0) {
            throw new IllegalArgumentException("Breadth score lookbacks and VIX threshold must be positive");
        }
    }

    public static BreadthScoreParameters from(BreadthScoreConfiguration configuration) {
        return new BreadthScoreParameters(configuration.getVersion(), configuration.getWeights(),
                configuration.getGreenThreshold(), configuration.getAmberThreshold(),
                configuration.getVixSpikePercent(), configuration.getRisingFallingLookbackSessions(),
                configuration.getExpansionContractionLookbackSessions());
    }

    public static BreadthScoreParameters defaults() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("AD_LINE_TREND", BreadthScoreDefaults.WEIGHT_AD_LINE_TREND);
        weights.put("BREADTH_50D", BreadthScoreDefaults.WEIGHT_BREADTH_50D);
        weights.put("BREADTH_200D", BreadthScoreDefaults.WEIGHT_BREADTH_200D);
        weights.put("MCCLELLAN_OSCILLATOR", BreadthScoreDefaults.WEIGHT_MCCLELLAN_OSCILLATOR);
        weights.put("MCCLELLAN_SUMMATION_INDEX", BreadthScoreDefaults.WEIGHT_MCCLELLAN_SUMMATION_INDEX);
        weights.put("NET_NEW_HIGHS_LOWS", BreadthScoreDefaults.WEIGHT_NET_NEW_HIGHS_LOWS);
        weights.put("ZWEIG_BREADTH_THRUST", BreadthScoreDefaults.WEIGHT_ZWEIG_BREADTH_THRUST);
        weights.put("VOLUME_BREADTH_TRIN", BreadthScoreDefaults.WEIGHT_VOLUME_BREADTH_TRIN);
        weights.put("BULLISH_PERCENT_PROXY", BreadthScoreDefaults.WEIGHT_BULLISH_PERCENT_PROXY);
        weights.put("SECTOR_PARTICIPATION", BreadthScoreDefaults.WEIGHT_SECTOR_PARTICIPATION);
        weights.put("PRICE_BREADTH_CONFIRMATION", BreadthScoreDefaults.WEIGHT_PRICE_BREADTH_CONFIRMATION);
        return new BreadthScoreParameters(1, weights, BreadthScoreDefaults.GREEN_THRESHOLD,
                BreadthScoreDefaults.AMBER_THRESHOLD, BreadthScoreDefaults.VIX_SPIKE_PERCENT,
                BreadthScoreDefaults.RISING_FALLING_TREND_LOOKBACK_SESSIONS,
                BreadthScoreDefaults.EXPANSION_CONTRACTION_LOOKBACK_SESSIONS);
    }
}
