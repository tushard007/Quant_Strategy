package org.factor_investing.quant_strategy.model.request;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.SensitivityMaBasis;

import java.time.LocalDate;
import java.util.List;

public record BreadthThresholdSensitivityRequest(
        LocalDate startDate, LocalDate endDate, double initialCapital, int entryRank, int retentionRank, String benchmark,
        double transactionCostPercent, double slippagePercent, double riskFreeRatePercent, String rebalanceMode,
        double bufferAmount, double maximumLeverageAmount, double borrowingInterestRatePercent,
        String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier, int cooldownWeeks,
        int benchmarkSmaPeriod, double breadthThresholdPercent, double weakExposureCapPercent,
        List<NiftyIndexName> universes, List<BreadthEntryMode> entryModes, List<Double> scoreCutoffs,
        List<SensitivityMaBasis> movingAverageBasesToCompare) {
}
