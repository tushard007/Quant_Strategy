package org.factor_investing.quant_strategy.momentum.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MomentumStockBackTestService {
@Autowired
    private TopMomentumStockService topMomentumStockService;

    public void runMomentumStrategyEachMonth(RebalenceStrategy rebalancedStrategy) {
        List<TopN_MomentumStock> topNStockList = topMomentumStockService.getAllTopMomentumStockByRebalenceStrategy(rebalancedStrategy);
        List<Date> distinctRebalancedDates = topNStockList.stream()
                .map(TopN_MomentumStock::getEndDate)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        System.out.println("Rebalanced Dates: " + distinctRebalancedDates);
    }
}
