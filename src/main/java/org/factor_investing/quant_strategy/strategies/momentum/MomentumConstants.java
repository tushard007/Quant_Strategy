package org.factor_investing.quant_strategy.strategies.momentum;

public class MomentumConstants {
    public static final int ENTRY_RANK = 10;
    public static final int RETENTION_RANK = 20;
    public static final int WEIGHT_12_MONTHS = 1;
    public static final int WEIGHT_6_MONTHS = 2;
    public static final int WEIGHT_3_MONTHS = 3;
    /** Current bar plus the 252-bar annual lookback. */
    public static final int MIN_DATA_POINTS = 253;
    public static final double MINIMUM_RETURN_THRESHOLD = 0.0; // 0% minimum for qualification
}
