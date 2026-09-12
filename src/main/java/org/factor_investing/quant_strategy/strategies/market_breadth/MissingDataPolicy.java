package org.factor_investing.quant_strategy.strategies.market_breadth;

/** How a stock with a missing price, missing volume, or suspended status on a given date is treated. */
public enum MissingDataPolicy {
    /** Excluded entirely from that date's advancer/decliner/coverage counts — not treated as unchanged, not carried forward. */
    EXCLUDE_FROM_SNAPSHOT
}
