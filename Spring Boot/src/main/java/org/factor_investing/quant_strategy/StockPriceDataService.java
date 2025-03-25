package org.factor_investing.quant_strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.StockPriceData;
import org.factor_investing.quant_strategy.repository.StockPriceDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
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


    public List<StockPriceData> getAllStockPriceData() {
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

    public Set<String> getUniqueStockTickers() {
        return stockRepository.findDistinctBySymbol();
    }
    public Set<Date> getUniqueStockPriceDates() {
        return stockRepository.findDistinctByDate();
    }
}