package org.factor_investing.quant_strategy.strategies.momentum;

public class MomentumConstants {
    public static final int TOP_NUMBER_MOMENTUM_STOCKS = 20;
    /** Current bar plus the 252-bar annual lookback. */
    public static final int MIN_DATA_POINTS = 253;
    public static final double MINIMUM_RETURN_THRESHOLD = 0.0; // 0% minimum for qualification
}
