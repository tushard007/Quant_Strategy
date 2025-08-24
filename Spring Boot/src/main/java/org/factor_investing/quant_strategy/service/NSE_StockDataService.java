package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NSE_StockDataService {
    @Autowired
    private NSEStockMasterDataRepository stockRepository;

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
        if (stockRepository.existsById(stockData.getId())) {
            stockRepository.save(stockData);
        } else {
            throw new IllegalArgumentException(STR."Stock data with ID \{stockData.getId()} does not exist.");
        }
    }
    public NSEStockMasterData getStockDataBySymbol(String symbol) {
        return stockRepository.findAll().stream()
                .filter(stock -> stock.getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);
    }

    public void deleteStockDataBySymbol(String symbol) {
        NSEStockMasterData stockData = getStockDataBySymbol(symbol);
        if (stockData != null) {
            stockRepository.delete(stockData);
        } else {
            throw new IllegalArgumentException(STR."Stock data with symbol \{symbol} does not exist.");
        }
    }
    public void saveAllStockData(Iterable<NSEStockMasterData> stockDataList) {
        stockRepository.saveAll(stockDataList);
    }



}
