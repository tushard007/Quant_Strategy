package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.NiftyIndexName;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record BreadthCalculationResult(NiftyIndexName universe,
                                       BreadthMethodology methodology,
                                       LocalDate tradingDate,
                                       BreadthCoverageResult coverage,
                                       Map<String, Double> indicatorValues,
                                       BreadthScoreResult score,
                                       List<BreadthDataQualityIssue> qualityIssues,
                                       List<BreadthOutlierWarning> outlierWarnings) {
}
