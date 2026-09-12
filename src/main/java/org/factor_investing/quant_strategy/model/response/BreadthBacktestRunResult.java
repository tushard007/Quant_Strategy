package org.factor_investing.quant_strategy.model.response;

public record BreadthBacktestRunResult(
        BreadthFilteredMomentumBacktestResult backtest,
        BreadthThresholdSensitivityResult sensitivity,
        ForwardReturnAnalysisResult forwardReturnContext) {
}
