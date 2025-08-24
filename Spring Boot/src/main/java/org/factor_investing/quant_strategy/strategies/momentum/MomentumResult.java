package org.factor_investing.quant_strategy.strategies.momentum;

import java.util.List;

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

    public List<StockMomentum> getAllStocks() { return allStocks; }
    public List<StockMomentum> getQualifiedStocks() { return qualifiedStocks; }
    public List<String> getTopStockNames() { return topStockNames; }
    public int getTotalAnalyzed() { return totalAnalyzed; }
    public int getQualifiedCount() { return qualifiedCount; }
    public boolean isValid() { return isValid; }
    public String getMessage() { return message; }
}


