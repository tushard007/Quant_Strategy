package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.momentum.service.MomentumStockBackTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-test")
public class MomentumStockBackTestController {
    @Autowired
    private MomentumStockBackTestService momentumStockBackTestService;

    @GetMapping("/momentum-strategy-data/{rebalancedStrategy}")
    public void getBackTestingMomentumStrategyData(@PathVariable RebalenceStrategy rebalancedStrategy) {
        momentumStockBackTestService.getBackTestingMomentumStrategyData(rebalancedStrategy);
    }
}
