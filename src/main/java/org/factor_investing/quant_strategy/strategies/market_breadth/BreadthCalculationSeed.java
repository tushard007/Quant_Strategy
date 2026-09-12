package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.util.Map;

public record BreadthCalculationSeed(boolean present,
                                     double adLine,
                                     double mcClellanEma19,
                                     double mcClellanEma39,
                                     double summationIndex) {
    public static BreadthCalculationSeed empty() {
        return new BreadthCalculationSeed(false, 0, 0, 0, 0);
    }

    public static BreadthCalculationSeed from(Map<String, Double> metrics) {
        return new BreadthCalculationSeed(true,
                metrics.getOrDefault(BreadthMetricKeys.AD_LINE, 0.0),
                metrics.getOrDefault(BreadthMetricKeys.MCCLELLAN_EMA_19, 0.0),
                metrics.getOrDefault(BreadthMetricKeys.MCCLELLAN_EMA_39, 0.0),
                metrics.getOrDefault(BreadthMetricKeys.MCCLELLAN_SUMMATION, 0.0));
    }
}
