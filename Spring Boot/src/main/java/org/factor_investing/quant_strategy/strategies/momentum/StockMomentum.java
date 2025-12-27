package org.factor_investing.quant_strategy.strategies.momentum;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StockMomentum {

    private final String stockName;
    private final Float oneYearReturn;
    private final Float sixMonthReturn;
    private final Float threeMonthReturn;
    private final boolean qualifiesForMomentum;
    private final LocalDate strategyRunDate;

    public StockMomentum(String stockName, Float oneYearReturn, Float sixMonthReturn, Float threeMonthReturn, LocalDate date) {
        this.stockName = stockName;
        this.oneYearReturn = oneYearReturn;
        this.sixMonthReturn = sixMonthReturn;
        this.threeMonthReturn = threeMonthReturn;
        this.qualifiesForMomentum = calculateQualification();
        this.strategyRunDate = date;
    }

    private boolean calculateQualification() {
        return !Double.isNaN(threeMonthReturn) &&
                !Double.isNaN(sixMonthReturn) &&
                threeMonthReturn > MomentumConstants.MINIMUM_RETURN_THRESHOLD &&
                sixMonthReturn > MomentumConstants.MINIMUM_RETURN_THRESHOLD;
    }

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
