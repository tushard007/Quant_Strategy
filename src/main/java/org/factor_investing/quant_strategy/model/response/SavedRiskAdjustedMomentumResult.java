package org.factor_investing.quant_strategy.model.response;

import java.sql.Date;

public record SavedRiskAdjustedMomentumResult(String stockName, float oneYearReturn, float sixMonthReturn, float threeMonthReturn,
                                  float volatility, float inverseVolWeight, Date strategyRunDate,
                                  int rank12Months, int rank6Months, int rank3Months, int totalRankScore) {
}
