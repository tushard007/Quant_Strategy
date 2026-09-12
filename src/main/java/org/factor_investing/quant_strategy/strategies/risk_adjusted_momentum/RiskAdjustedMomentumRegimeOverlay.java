package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

/** As-of-date market-regime snapshot (benchmark trend + universe breadth) and its effect on the entry cohort size. */
public record RiskAdjustedMomentumRegimeOverlay(String benchmark, double breadthPercent, boolean benchmarkAboveSma,
                                                 double exposureCapPercent, boolean newBuysAllowed, int effectiveEntryRank) {
}
