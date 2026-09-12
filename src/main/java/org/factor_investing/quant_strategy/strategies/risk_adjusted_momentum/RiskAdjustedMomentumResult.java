package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import lombok.Getter;

import java.util.List;

@Getter
public class RiskAdjustedMomentumResult {
    private final List<RiskAdjustedMomentum> allStocks;
    private final List<RiskAdjustedMomentum> qualifiedStocks;
    private final List<String> topStockNames;
    private final List<String> retainedStockNames;
    private final int totalAnalyzed;
    private final int qualifiedCount;
    private final boolean isValid;
    private final String message;
    private final RiskAdjustedMomentumRegimeOverlay regimeOverlay;

    public RiskAdjustedMomentumResult(List<RiskAdjustedMomentum> allStocks, List<RiskAdjustedMomentum> qualifiedStocks,
                                       List<String> topStockNames, List<String> retainedStockNames, boolean isValid, String message) {
        this(allStocks, qualifiedStocks, topStockNames, retainedStockNames, isValid, message, null);
    }

    public RiskAdjustedMomentumResult(List<RiskAdjustedMomentum> allStocks, List<RiskAdjustedMomentum> qualifiedStocks,
                                       List<String> topStockNames, List<String> retainedStockNames, boolean isValid, String message,
                                       RiskAdjustedMomentumRegimeOverlay regimeOverlay) {
        this.allStocks = allStocks;
        this.qualifiedStocks = qualifiedStocks;
        this.topStockNames = topStockNames;
        this.retainedStockNames = retainedStockNames;
        this.totalAnalyzed = allStocks != null ? allStocks.size() : 0;
        this.qualifiedCount = qualifiedStocks != null ? qualifiedStocks.size() : 0;
        this.isValid = isValid;
        this.message = message;
        this.regimeOverlay = regimeOverlay;
    }
}
