package org.factor_investing.quant_strategy.strategies.momentum;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


public class StockMomentum {
    private final String stockName;
    private final Float oneYearReturn;
    private final Float sixMonthReturn;
    private final Float threeMonthReturn;
    private final boolean qualifiesForMomentum;

    public StockMomentum(String stockName, Float oneYearReturn, Float sixMonthReturn, Float threeMonthReturn) {
        this.stockName = stockName;
        this.oneYearReturn = oneYearReturn;
        this.sixMonthReturn = sixMonthReturn;
        this.threeMonthReturn = threeMonthReturn;
        this.qualifiesForMomentum = calculateQualification();
    }

    private boolean calculateQualification() {
        return !Double.isNaN(threeMonthReturn) &&
                !Double.isNaN(sixMonthReturn) &&
                threeMonthReturn > MomentumConstants.MINIMUM_RETURN_THRESHOLD &&
                sixMonthReturn > MomentumConstants.MINIMUM_RETURN_THRESHOLD;
    }

    public String getStockName() { return stockName; }
    public double getOneYearReturn() { return oneYearReturn; }
    public double getSixMonthReturn() { return sixMonthReturn; }
    public double getThreeMonthReturn() { return threeMonthReturn; }
    public boolean qualifiesForMomentum() { return qualifiesForMomentum; }

    @Override
    public String toString() {
        return String.format("%s: 1Y=%.2f%%, 6M=%.2f%%, 3M=%.2f%% %s",
                stockName,
                oneYearReturn * 100,
                sixMonthReturn * 100,
                threeMonthReturn * 100,
                qualifiesForMomentum ? "✓" : "✗");
    }
}
