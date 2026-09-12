package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

public class RiskAdjustedMomentumConstants {
    public static final int ENTRY_RANK = 20;
    public static final int RETENTION_RANK = 40;
    public static final int WEIGHT_12_MONTHS = 1;
    public static final int WEIGHT_6_MONTHS = 2;
    public static final int WEIGHT_3_MONTHS = 3;
    /** Trading bars skipped to avoid the short-term reversal effect in the 12-month leg (classic "12-1" momentum). */
    public static final int SKIP_MONTH_BARS = 21;
    /** Current bar plus the 252-bar annual lookback plus the 1-month skip. */
    public static final int MIN_DATA_POINTS = 253 + SKIP_MONTH_BARS;
    public static final double MINIMUM_RETURN_THRESHOLD = 0.0; // 0% minimum for qualification
    /** Trailing window used for inverse-volatility position sizing (same window as the 3-month leg). */
    public static final int VOLATILITY_LOOKBACK_BARS = 63;

    // Default risk-overlay parameters (wired in by default, but tunable per request/backtest run).
    public static final String DEFAULT_STOP_MODEL = "ATR";
    public static final double DEFAULT_TRAILING_STOP_PERCENT = 0;
    public static final int DEFAULT_ATR_PERIOD = 14;
    public static final double DEFAULT_ATR_MULTIPLIER = 3;
    public static final int DEFAULT_COOLDOWN_WEEKS = 2;
    public static final int DEFAULT_BENCHMARK_SMA_PERIOD = 200;
    public static final double DEFAULT_BREADTH_THRESHOLD_PERCENT = 20;
    public static final double DEFAULT_WEAK_EXPOSURE_CAP_PERCENT = 50;
    public static final String DEFAULT_REBALANCE_MODE = "EQUAL_WEIGHT";
    public static final String DEFAULT_BENCHMARK = "NIFTY 500";

    /** Allocation mode for the Analysis feature's per-run position weights: EQUAL_WEIGHT or INVERSE_VOL, applied to the top ENTRY_RANK cohort. */
    public static final String DEFAULT_ALLOCATION_MODE = "EQUAL_WEIGHT";
}
