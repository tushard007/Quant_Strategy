package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthAlertType;

import java.util.Map;

public record BreadthAlertCandidate(BreadthAlertType type,
                                    Map<String, Double> triggerValues,
                                    String message) {
    public BreadthAlertCandidate {
        triggerValues = Map.copyOf(triggerValues);
    }
}
