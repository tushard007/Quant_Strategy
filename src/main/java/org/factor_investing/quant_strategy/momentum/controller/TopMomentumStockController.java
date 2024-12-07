package org.factor_investing.quant_strategy.momentum.controller;

import org.factor_investing.quant_strategy.momentum.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.momentum.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.momentum.service.TopMomentumStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.List;

@RestController
@RequestMapping("/momeuntumStock")
public class TopMomentumStockController {
    @Autowired
    private TopMomentumStockService topMomentumStockService;
    @PostMapping("/{rebalenceStrategy}/{rebalenceDate}")
    public List<TopN_MomentumStock> getStockReturnForYear(@PathVariable Date rebalenceDate, @PathVariable RebalenceStrategy rebalenceStrategy) throws InterruptedException {
         return topMomentumStockService.CalculateStockReturnForYear( rebalenceDate,rebalenceStrategy);
    }
}
