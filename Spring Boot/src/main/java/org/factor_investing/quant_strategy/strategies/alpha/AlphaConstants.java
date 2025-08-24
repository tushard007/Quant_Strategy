package org.factor_investing.quant_strategy.strategies.alpha;

public class AlphaConstants {
    public static final double RISK_FREE_RATE = 0.03; // 3% risk-free
    public static final double MARKET_RETURN = 0.12;  // 12% annual benchmark return (provided by you)
    public static final int TRADING_DAYS_PER_YEAR = 252;
    public static final int MIN_DATA_POINTS = 30;

    // For beta approximation (if no market data series provided)
    public static final double DEFAULT_MARKET_VARIANCE = 0.02;
    public static final double DEFAULT_MARKET_CORRELATION = 0.8;
    public static final double MIN_BETA = -5.0;
    public static final double MAX_BETA = 5.0;
}
