// java
package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NSE_StockDataService {
    private final NSEStockMasterDataRepository stockRepository;

    public NSE_StockDataService(NSEStockMasterDataRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public void saveStockData(NSEStockMasterData stockData) {
        stockRepository.save(stockData);
    }

    public NSEStockMasterData getStockDataById(Long id) {
        return stockRepository.findById(id).orElse(null);
    }

    public void deleteStockData(Long id) {
        stockRepository.deleteById(id);
    }

    public List<NSEStockMasterData> getAllStockData() {
        return stockRepository.findAll();
    }

    public void updateStockData(NSEStockMasterData stockData) {
        if (stockData == null || stockData.getId() == null) {
            throw new IllegalArgumentException("Stock data or ID must not be null.");
        }
        if (stockRepository.existsById(stockData.getId())) {
            stockRepository.save(stockData);
        } else {
            throw new IllegalArgumentException("Stock data with ID " + stockData.getId() + " does not exist.");
        }
    }

    public NSEStockMasterData getStockDataBySymbol(String symbol) {
        if (symbol == null) return null;
        return stockRepository.findAll().stream()
                .filter(stock -> symbol.equalsIgnoreCase(stock.getSymbol()))
                .findFirst()
                .orElse(null);
    }

    public void deleteStockDataBySymbol(String symbol) {
        NSEStockMasterData stockData = getStockDataBySymbol(symbol);
        if (stockData != null) {
            stockRepository.delete(stockData);
        } else {
            throw new IllegalArgumentException("Stock data with symbol " + symbol + " does not exist.");
        }
    }

    public void saveAllStockData(Iterable<NSEStockMasterData> stockDataList) {
        stockRepository.saveAll(stockDataList);
    }
}
