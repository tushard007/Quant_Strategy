package org.factor_investing.quant_strategy.technical_analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SuperTrendResult {
    private final double closingPrice;
    private final double superTrendValue;
    private final double percentageDifference;
    private final double upperBand;
    private final double lowerBand;
    private final String trend;
}
