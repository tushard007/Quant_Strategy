package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;

public record BreadthCoverageResult(int expectedCount, int observedCount, int eligibleCount,
                                     double coveragePercent, BreadthSnapshotQuality qualityStatus) {
}
