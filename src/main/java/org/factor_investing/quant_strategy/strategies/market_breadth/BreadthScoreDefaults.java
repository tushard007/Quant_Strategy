package org.factor_investing.quant_strategy.strategies.market_breadth;

/**
 * Seed values for the v1 Breadth Score configuration. Superseded at runtime once BRD-013
 * (versioned score configuration schema) exists — kept here as named constants so nothing
 * downstream hardcodes these numbers directly.
 */
public class BreadthScoreDefaults {
    // Component weights, summing to 100. India VIX is a critical-override filter, not a weighted component.
    public static final int WEIGHT_AD_LINE_TREND = 10;
    public static final int WEIGHT_BREADTH_50D = 15;
    public static final int WEIGHT_BREADTH_200D = 15;
    public static final int WEIGHT_MCCLELLAN_OSCILLATOR = 10;
    public static final int WEIGHT_MCCLELLAN_SUMMATION_INDEX = 10;
    public static final int WEIGHT_NET_NEW_HIGHS_LOWS = 10;
    public static final int WEIGHT_ZWEIG_BREADTH_THRUST = 5;
    public static final int WEIGHT_VOLUME_BREADTH_TRIN = 5;
    public static final int WEIGHT_BULLISH_PERCENT_PROXY = 10;
    public static final int WEIGHT_SECTOR_PARTICIPATION = 5;
    public static final int WEIGHT_PRICE_BREADTH_CONFIRMATION = 5;
    public static final int TOTAL_WEIGHT = WEIGHT_AD_LINE_TREND + WEIGHT_BREADTH_50D + WEIGHT_BREADTH_200D
            + WEIGHT_MCCLELLAN_OSCILLATOR + WEIGHT_MCCLELLAN_SUMMATION_INDEX + WEIGHT_NET_NEW_HIGHS_LOWS
            + WEIGHT_ZWEIG_BREADTH_THRUST + WEIGHT_VOLUME_BREADTH_TRIN + WEIGHT_BULLISH_PERCENT_PROXY
            + WEIGHT_SECTOR_PARTICIPATION + WEIGHT_PRICE_BREADTH_CONFIRMATION;

    // Regime thresholds, on a 0-100 score.
    public static final double GREEN_THRESHOLD = 70;
    public static final double AMBER_THRESHOLD = 40;

    /** India VIX single-session percentage rise that counts as a volatility spike critical override. */
    public static final double VIX_SPIKE_PERCENT = 10;

    // Trend lookbacks, in sessions.
    public static final int RISING_FALLING_TREND_LOOKBACK_SESSIONS = 5;
    public static final int EXPANSION_CONTRACTION_LOOKBACK_SESSIONS = 10;

    static {
        if (TOTAL_WEIGHT != 100) {
            throw new IllegalStateException("Breadth score component weights must sum to 100, got " + TOTAL_WEIGHT);
        }
    }

    private BreadthScoreDefaults() {
    }
}
