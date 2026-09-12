package org.factor_investing.quant_strategy.strategies.market_breadth;

public class BreadthCalculationConventions {
    /** Configurable per stock/index-average calculation; EMA is the default. */
    public static final MovingAverageType DEFAULT_MOVING_AVERAGE_TYPE = MovingAverageType.EMA;

    /** New-high/new-low and "52-week" lookback, expressed as valid trading sessions rather than calendar days. */
    public static final int NEW_HIGH_LOW_LOOKBACK_SESSIONS = 252;

    /** A stock counts as "unchanged" only on an exact 0.00% close-to-close move; unchanged stocks are excluded from the A/D denominator. */
    public static final double UNCHANGED_PRICE_EPSILON_PERCENT = 0.0;

    public static final MissingDataPolicy MISSING_DATA_POLICY = MissingDataPolicy.EXCLUDE_FROM_SNAPSHOT;

    /** Coverage below this percent, but at/above the warning threshold, yields a WARNING quality snapshot. */
    public static final double MIN_COVERAGE_VALID_PERCENT = 90;

    /** Coverage below this percent yields an INVALID quality snapshot. */
    public static final double MIN_COVERAGE_WARNING_PERCENT = 75;

    /**
     * Signals are calculated from a trading date's EOD close. The earliest a signal may affect a
     * trading action (entry, exit, exposure change) is the next trading session's open — never
     * the same close the signal was computed from. Enforced by the calculation orchestrator
     * (BRD-041) and verified by the backtest look-ahead tests (BRD-103).
     */
    public static final int MIN_SESSIONS_BEFORE_EXECUTION = 1;

    private BreadthCalculationConventions() {
    }
}
