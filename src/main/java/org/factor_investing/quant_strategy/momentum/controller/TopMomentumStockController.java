package org.factor_investing.quant_strategy.momentum.controller;

import org.factor_investing.quant_strategy.momentum.service.TopMomentumStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;

@RestController
@RequestMapping("/momeuntumStock")
public class TopMomentumStockController {
    @Autowired
    private TopMomentumStockService topMomentumStockService;
    @GetMapping("/{ticker}/{rebalenceDate}")
    public float getStockPriceDate(@PathVariable String ticker, @PathVariable Date rebalenceDate) {
        return topMomentumStockService.getStockPrice(ticker, rebalenceDate);
    }
}
