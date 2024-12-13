package org.factor_investing.quant_strategy.momentum.controller;

import org.factor_investing.quant_strategy.momentum.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.momentum.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.momentum.service.TopMomentumStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.List;

@RestController
@RequestMapping("/momentumStock")
public class TopMomentumStockController {
    @Autowired
    private TopMomentumStockService topMomentumStockService;
    @PostMapping("/{rebalancedStrategy}/{rebalancedDate}")
    public List<TopN_MomentumStock> getStockReturnForYear(@PathVariable Date rebalancedDate, @PathVariable RebalenceStrategy rebalancedStrategy) {
         return topMomentumStockService.CalculateStockReturnForYear( rebalancedDate,rebalancedStrategy);
    }
}
