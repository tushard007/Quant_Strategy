package org.factor_investing.quant_strategy.strategies.market_breadth;

/** Stable persisted keys used by breadth snapshots, scoring, alerts, and APIs. */
public final class BreadthMetricKeys {
    public static final String ADVANCERS = "ADVANCERS";
    public static final String DECLINERS = "DECLINERS";
    public static final String UNCHANGED = "UNCHANGED";
    public static final String ELIGIBLE_DAILY = "ELIGIBLE_DAILY";
    public static final String NET_ADVANCES = "NET_ADVANCES";
    public static final String AD_LINE = "AD_LINE";
    public static final String AD_LINE_RISING = "AD_LINE_RISING";
    public static final String AD_RATIO = "AD_RATIO";
    public static final String AD_RATIO_5D = "AD_RATIO_5D";
    public static final String AD_RATIO_10D = "AD_RATIO_10D";
    public static final String ADVANCE_PROPORTION = "ADVANCE_PROPORTION";
    public static final String BREADTH_50D_PERCENT = "BREADTH_50D_PERCENT";
    public static final String BREADTH_200D_PERCENT = "BREADTH_200D_PERCENT";
    public static final String BREADTH_50D_RISING = "BREADTH_50D_RISING";
    public static final String BREADTH_200D_RISING = "BREADTH_200D_RISING";
    public static final String BREADTH_50D_TREND = "BREADTH_50D_TREND";
    public static final String BREADTH_200D_TREND = "BREADTH_200D_TREND";
    public static final String MCCLELLAN_EMA_19 = "MCCLELLAN_EMA_19";
    public static final String MCCLELLAN_EMA_39 = "MCCLELLAN_EMA_39";
    public static final String MCCLELLAN_OSCILLATOR = "MCCLELLAN_OSCILLATOR";
    public static final String MCCLELLAN_ZERO_CROSS = "MCCLELLAN_ZERO_CROSS";
    public static final String MCCLELLAN_SUMMATION = "MCCLELLAN_SUMMATION";
    public static final String SUMMATION_TREND = "SUMMATION_TREND";
    public static final String SUMMATION_TREND_FLIP = "SUMMATION_TREND_FLIP";
    public static final String NEW_HIGHS = "NEW_HIGHS";
    public static final String NEW_LOWS = "NEW_LOWS";
    public static final String NET_NEW_HIGHS = "NET_NEW_HIGHS";
    public static final String NEW_HIGH_LOW_RATIO = "NEW_HIGH_LOW_RATIO";
    public static final String NET_NEW_HIGHS_EXPANDING_5D = "NET_NEW_HIGHS_EXPANDING_5D";
    public static final String NET_NEW_HIGHS_EXPANDING_10D = "NET_NEW_HIGHS_EXPANDING_10D";
    public static final String NET_NEW_HIGHS_TREND_5D = "NET_NEW_HIGHS_TREND_5D";
    public static final String NET_NEW_HIGHS_TREND_10D = "NET_NEW_HIGHS_TREND_10D";
    public static final String ZWEIG_RATIO_10D = "ZWEIG_RATIO_10D";
    public static final String ZWEIG_THRUST = "ZWEIG_THRUST";
    public static final String ADVANCING_VOLUME = "ADVANCING_VOLUME";
    public static final String DECLINING_VOLUME = "DECLINING_VOLUME";
    public static final String UP_DOWN_VOLUME_RATIO = "UP_DOWN_VOLUME_RATIO";
    public static final String TRIN = "TRIN";
    public static final String BULLISH_PERCENT_PROXY = "BULLISH_PERCENT_PROXY";
    public static final String SECTOR_PARTICIPATION = "SECTOR_PARTICIPATION";
    public static final String SECTOR_COUNT = "SECTOR_COUNT";
    public static final String BENCHMARK_CLOSE = "BENCHMARK_CLOSE";
    public static final String BENCHMARK_NEW_HIGH_20D = "BENCHMARK_NEW_HIGH_20D";
    public static final String BENCHMARK_NEW_HIGH_50D = "BENCHMARK_NEW_HIGH_50D";
    public static final String BENCHMARK_NEAR_HIGH = "BENCHMARK_NEAR_HIGH";
    public static final String BULLISH_CONFIRMATION = "BULLISH_CONFIRMATION";
    public static final String BEARISH_DIVERGENCE = "BEARISH_DIVERGENCE";
    public static final String NARROW_LEADERSHIP = "NARROW_LEADERSHIP";
    public static final String INDIA_VIX_CLOSE = "INDIA_VIX_CLOSE";
    public static final String INDIA_VIX_EMA_20 = "INDIA_VIX_EMA_20";
    public static final String INDIA_VIX_CHANGE_PERCENT = "INDIA_VIX_CHANGE_PERCENT";
    public static final String INDIA_VIX_ABOVE_EMA = "INDIA_VIX_ABOVE_EMA";
    public static final String INDIA_VIX_SPIKE = "INDIA_VIX_SPIKE";
    public static final String EXPECTED_COUNT = "EXPECTED_COUNT";
    public static final String OBSERVED_COUNT = "OBSERVED_COUNT";
    public static final String ELIGIBLE_COUNT = "ELIGIBLE_COUNT";

    private BreadthMetricKeys() {
    }
}
