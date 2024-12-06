package org.factor_investing.quant_strategy.momentum.service;

import org.factor_investing.quant_strategy.StockPriceDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class TopMomentumStockService {
    @Autowired
    public StockPriceDataService stockPriceDataService;

public float getStockPrice(String symbol, Date rebalenceDate) {
return stockPriceDataService.getStockPrice(symbol, rebalenceDate);
}
}
