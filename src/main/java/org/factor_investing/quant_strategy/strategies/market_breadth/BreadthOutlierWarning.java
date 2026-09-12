package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.time.LocalDate;

public record BreadthOutlierWarning(String symbol, LocalDate date, WarningType warningType, String detail) {
    public enum WarningType {
        EXTREME_PRICE_MOVE,
        VOLUME_DISCONTINUITY,
        POSSIBLE_UNADJUSTED_CORPORATE_ACTION
    }
}
