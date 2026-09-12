package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.time.LocalDate;

public record BreadthDailyStatistics(LocalDate date,
                                     int advancers,
                                     int decliners,
                                     int unchanged,
                                     int eligibleDaily,
                                     int netAdvances,
                                     double adRatio,
                                     double advanceProportion,
                                     double breadth50Percent,
                                     double breadth200Percent,
                                     int newHighs,
                                     int newLows,
                                     int netNewHighs,
                                     double newHighLowRatio,
                                     double advancingVolume,
                                     double decliningVolume,
                                     double upDownVolumeRatio,
                                     double trin,
                                     double bullishPercentProxy) {
}
