package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
    public class StockDataService {

        private final StockDataRepository stockDataRepository;

        public StockDataService(StockDataRepository stockDataRepository) {
            this.stockDataRepository= stockDataRepository;
        }

    public List<StockPricesJson> getAllStockData() {
        return stockDataRepository.findAll();
    }

}
