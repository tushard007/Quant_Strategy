package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.NiftyIndexName;

import java.time.LocalDate;
import java.util.List;

public record ForwardReturnAnalysisResult(NiftyIndexName universe, LocalDate from, LocalDate to, String benchmarkIndex,
        int totalObservations, int observationsMissingForward20, int observationsMissingForward60,
        List<ForwardReturnStatistics> byRegime, List<ForwardReturnStatistics> byScoreBucket,
        List<ForwardReturnStatistics> byRegimeAndScoreBucket) {
}
