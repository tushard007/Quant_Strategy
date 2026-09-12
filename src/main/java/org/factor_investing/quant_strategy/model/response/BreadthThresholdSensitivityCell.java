package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.SensitivityMaBasis;

public record BreadthThresholdSensitivityCell(
        NiftyIndexName universe, BreadthEntryMode entryMode, double scoreCutoff, SensitivityMaBasis maBasis,
        double baselineTotalReturn, double baselineCagr, double baselineSharpeRatio, double baselineMaxDrawdown,
        double filteredTotalReturn, double filteredCagr, double filteredSharpeRatio, double filteredMaxDrawdown,
        long qualifyingSnapshotSampleCount, int rebalanceCount) {
}
