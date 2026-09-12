package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.util.List;

public record BreadthReferenceDataReport(List<IndexReferenceDataIssue> issues) {
    public boolean isClean() {
        return issues.isEmpty();
    }
}
