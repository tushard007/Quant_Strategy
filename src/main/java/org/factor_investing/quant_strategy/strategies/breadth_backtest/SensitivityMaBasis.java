package org.factor_investing.quant_strategy.strategies.breadth_backtest;

/**
 * Which "trend/breadth is favorable" signal a sensitivity cell filters on. Scoped substitute for a true
 * EMA-vs-SMA breadth recomputation (BRD-093): compares the real, persisted EMA-based breadth score/regime
 * against the momentum service's own pre-existing SMA-based benchmark/breadth overlay.
 */
public enum SensitivityMaBasis {
    /** Real breadth pipeline: EMA-based component scores rolled up into a persisted daily score/regime. */
    PERSISTED_BREADTH_SCORE,
    /** The momentum backtest's own built-in SMA benchmark-trend + universe-breadth gate; no persisted snapshot involved. */
    LEGACY_SMA_OVERLAY
}
