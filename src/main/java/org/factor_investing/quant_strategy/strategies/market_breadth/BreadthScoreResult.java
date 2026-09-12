package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthRegime;

import java.util.Map;

public record BreadthScoreResult(double score,
                                 BreadthRegime regime,
                                 Map<String, Double> componentScores,
                                 Map<String, String> componentReasons) {
}
