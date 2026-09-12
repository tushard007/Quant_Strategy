package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RiskAdjustedMomentum {

    private final String stockName;
    /** 12-1 momentum return: 12-month return ending one month ago (skips the most recent month). */
    private final Float oneYearReturn;
    private final Float sixMonthReturn;
    private final Float threeMonthReturn;
    /** Annualized stdev of daily returns over the trailing 63 bars, used for inverse-volatility sizing. */
    private final Float volatility;
    private final boolean qualifiesForMomentum;
    private final LocalDate strategyRunDate;
    /** Hypothetical stop-loss level for a fresh entry today (regime-gated entry cohort only). */
    private Float stopLossLevel;
    /** Rank and blended score among the qualified cohort, set only by calculateAndRankMomentum(); null for non-qualifying assets. */
    private Integer rank12Months;
    private Integer rank6Months;
    private Integer rank3Months;
    private Integer totalRankScore;

    public RiskAdjustedMomentum(String stockName, Float oneYearReturn, Float sixMonthReturn, Float threeMonthReturn,
                                 Float volatility, LocalDate date) {
        this.stockName = stockName;
        this.oneYearReturn = oneYearReturn;
        this.sixMonthReturn = sixMonthReturn;
        this.threeMonthReturn = threeMonthReturn;
        this.volatility = volatility;
        this.qualifiesForMomentum = calculateQualification();
        this.strategyRunDate = date;
    }

    private boolean calculateQualification() {
        return !Double.isNaN(oneYearReturn) &&
                !Double.isNaN(threeMonthReturn) &&
                !Double.isNaN(sixMonthReturn) &&
                oneYearReturn > RiskAdjustedMomentumConstants.MINIMUM_RETURN_THRESHOLD &&
                threeMonthReturn > RiskAdjustedMomentumConstants.MINIMUM_RETURN_THRESHOLD &&
                sixMonthReturn > RiskAdjustedMomentumConstants.MINIMUM_RETURN_THRESHOLD;
    }

    @Override
    public String toString() {
        return String.format("%s: 12-1M=%.2f%%, 6M=%.2f%%, 3M=%.2f%%, Vol=%.2f%% %s",
                stockName,
                oneYearReturn,
                sixMonthReturn,
                threeMonthReturn,
                volatility,
                qualifiesForMomentum ? "✓" : "✗");
    }
}
