package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NSE_StockMasterData;
import org.factor_investing.quant_strategy.repository.NSE_StockDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NSE_StockDataService {
    @Autowired
    private NSE_StockDataRepository stockRepository;

    public void saveStockData(NSE_StockMasterData stockData) {
        stockRepository.save(stockData);
    }
    public NSE_StockMasterData getStockDataById(Long id) {
        return stockRepository.findById(id).orElse(null);
    }
    public void deleteStockData(Long id) {
        stockRepository.deleteById(id);
    }
    public List<NSE_StockMasterData> getAllStockData() {
        return stockRepository.findAll();
    }
    public void updateStockData(NSE_StockMasterData stockData) {
        if (stockRepository.existsById(stockData.getId())) {
            stockRepository.save(stockData);
        } else {
            throw new IllegalArgumentException(STR."Stock data with ID \{stockData.getId()} does not exist.");
        }
    }
    public NSE_StockMasterData getStockDataBySymbol(String symbol) {
        return stockRepository.findAll().stream()
                .filter(stock -> stock.getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);
    }

    public void deleteStockDataBySymbol(String symbol) {
        NSE_StockMasterData stockData = getStockDataBySymbol(symbol);
        if (stockData != null) {
            stockRepository.delete(stockData);
        } else {
            throw new IllegalArgumentException("Stock data with symbol " + symbol + " does not exist.");
        }
    }
    public void saveAllStockData(Iterable<NSE_StockMasterData> stockDataList) {
        stockRepository.saveAll(stockDataList);
    }



}
