package org.factor_investing.quant_strategy.strategies.momentum;

import lombok.Getter;

import java.util.List;

@Getter
public class MomentumResult {
    private final List<StockMomentum> allStocks;
    private final List<StockMomentum> qualifiedStocks;
    private final List<String> topStockNames;
    private final int totalAnalyzed;
    private final int qualifiedCount;
    private final boolean isValid;
    private final String message;

    public MomentumResult(List<StockMomentum> allStocks, List<StockMomentum> qualifiedStocks,
                          List<String> topStockNames, boolean isValid, String message) {
        this.allStocks = allStocks;
        this.qualifiedStocks = qualifiedStocks;
        this.topStockNames = topStockNames;
        this.totalAnalyzed = allStocks != null ? allStocks.size() : 0;
        this.qualifiedCount = qualifiedStocks != null ? qualifiedStocks.size() : 0;
        this.isValid = isValid;
        this.message = message;
    }
}


