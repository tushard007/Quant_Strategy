package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.service.TopMomentumStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/momentum-stock")
public class TopMomentumStockController {
    @Autowired
    private TopMomentumStockService topMomentumStockService;

    @PostMapping("/{rebalancedStrategy}/{startDate}/{endDate}")
    public void runTopMomentumStocksForPeriod(@PathVariable RebalenceStrategy rebalancedStrategy, @PathVariable Date startDate, @PathVariable Date endDate) {
        topMomentumStockService.runTopMomentumStocksForPeriod(rebalancedStrategy, startDate, endDate);
    }

    @PostMapping("/{rebalancedStrategy}/{rebalancedDate}")
    public List<TopN_MomentumStock> getStockReturnForYear(@PathVariable Date rebalancedDate, @PathVariable RebalenceStrategy rebalancedStrategy) {
         return topMomentumStockService.calculateStockReturn( rebalancedDate,rebalancedStrategy);
    }

    @GetMapping("/TopMomentumStock/{rebalancedStrategy}/groupByRebalencedDate")
    public Map<java.util.Date, List<TopN_MomentumStock>> getTopMomentumStockGroupByRebalencedDate(@PathVariable RebalenceStrategy rebalancedStrategy) {
        return topMomentumStockService.getTopNStockGroupByRebalencedDate(rebalancedStrategy);
    }
}
