package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;

import java.time.LocalDate;
import java.util.List;

public record BreadthFilteredMomentumBacktestResult(
        RiskAdjustedMomentumBacktestResult baseline, RiskAdjustedMomentumBacktestResult breadthFiltered,
        NiftyIndexName breadthUniverse, BreadthEntryMode breadthEntryMode, double breadthScoreCutoff,
        BreadthMethodology methodology, int representativeScoreConfigurationVersion,
        List<Integer> scoreConfigurationVersionsObserved, List<BreadthCoverageNote> coverageNotes) {
    public record BreadthCoverageNote(LocalDate signalDate, String reason) {}
}
