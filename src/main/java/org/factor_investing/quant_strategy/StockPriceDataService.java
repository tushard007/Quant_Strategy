package org.factor_investing.quant_strategy;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@Getter
@Setter
public class StockPriceDataService {

    @Autowired
    private StockPriceDataRepository stockRepository;

    // Create or Update StockPriceData
    public StockPriceData saveOrUpdateStockPrice(StockPriceData stockPriceData) {
        return stockRepository.save(stockPriceData);
    }

    @PostConstruct
    // Get All StockPriceData
    public List<StockPriceData> getAllStockPrices() {
        System.out.println("Inside getAllStockPrices:"+stockRepository.findAll().size());
        return stockRepository.findAll();
    }

    // Get StockPriceData by ID
    public Optional<StockPriceData> getStockPriceById(int id) {
        return stockRepository.findById(id);
    }

    // Delete StockPriceData by ID
    public void deleteStockPriceById(int id) {
        stockRepository.deleteById(id);
    }

    public float getStockPrice(String symbol, Date rebalenceDate) {
        List<StockPriceData> stockPriceDataList = getAllStockPrices();
        for (StockPriceData priceData : stockPriceDataList) {
            if (priceData.getStockTicker().equalsIgnoreCase(symbol) &&
                    priceData.getPriceDate().equals(rebalenceDate))
         return priceData.getStockPrice();

        }
        // If no match is found, throw an exception or return a default value
        throw new IllegalArgumentException("No stock price found for the given symbol and date.");
    }

    public Set<String> getUniqueStockTickers() {
        return stockRepository.findDistinctByStockTicker();
    }
    public Set<java.util.Date> getUniqueStockPriceDates() {
        return stockRepository.findDistinctByPriceDate();
    }
}