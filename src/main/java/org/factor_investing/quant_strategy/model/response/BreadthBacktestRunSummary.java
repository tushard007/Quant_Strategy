package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.BreadthBacktestRunType;
import org.factor_investing.quant_strategy.model.BreadthBacktestStatus;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BreadthBacktestRunSummary(
        UUID id, Instant createdAt, BreadthBacktestRunType runType, BreadthBacktestStatus status,
        LocalDate startDate, LocalDate endDate, NiftyIndexName breadthUniverse, BreadthEntryMode breadthEntryMode,
        Double breadthScoreCutoff, Double baselineTotalReturn, Double filteredTotalReturn,
        Double filteredSharpeRatio, Double filteredMaximumDrawdown, Integer sampleCount) {
}
