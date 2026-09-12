package org.factor_investing.quant_strategy.model.response;

public record ForwardReturnStatistics(String groupKey, int sampleCount,
        double meanReturn20, double medianReturn20, double positiveRatePercent20, double volatility20, double worstReturn20,
        double meanReturn60, double medianReturn60, double positiveRatePercent60, double volatility60, double worstReturn60) {
}
