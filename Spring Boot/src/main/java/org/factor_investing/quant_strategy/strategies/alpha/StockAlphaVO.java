package org.factor_investing.quant_strategy.strategies.alpha;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockAlphaVO {
    private String stockName;
    private double alpha;
    private double annualizedReturn;
    private double beta;
    private int dataPoints;
    private boolean success;
    private String message;
    private double annualizedVolatility;
    private double sharpeRatio;
}

