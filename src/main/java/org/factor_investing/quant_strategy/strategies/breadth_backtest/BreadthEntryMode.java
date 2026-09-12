package org.factor_investing.quant_strategy.strategies.breadth_backtest;

/** How the breadth regime/score for a given signal date gates or scales the momentum backtest's exposure. */
public enum BreadthEntryMode {
    /** No breadth filtering — identical to a plain {@code run(...)} call. */
    BASELINE,
    /** New buys allowed only while the breadth regime is GREEN. */
    GREEN_ONLY,
    /** New buys allowed unless the breadth regime is RED. */
    GREEN_AMBER,
    /** Exposure cap scaled continuously by the breadth score (0-100), regime ignored. */
    SCORE_SCALED
}
