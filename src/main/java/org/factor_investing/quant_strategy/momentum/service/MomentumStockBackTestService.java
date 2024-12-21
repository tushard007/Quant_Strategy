package org.factor_investing.quant_strategy.momentum.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.model.StockSignal;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.util.GenericMatchingUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MomentumStockBackTestService {
@Autowired
    private TopMomentumStockService topMomentumStockService;

    public void getBackTestingMomentumStrategyData(RebalenceStrategy rebalancedStrategy) {
        List<TopN_MomentumStock> topNStockList = topMomentumStockService.getAllTopMomentumStockByRebalenceStrategy(rebalancedStrategy);

        // Get all unique rebalanced dates
        List<Date> distinctRebalancedDates = topNStockList.stream()
                .map(TopN_MomentumStock::getEndDate)
                .distinct()
                .collect(Collectors.toList());  // No need for explicitly creating an ArrayList here

        // Use an iterator to avoid ConcurrentModificationException
        Iterator<Date> dateIterator = distinctRebalancedDates.iterator();
        if (!dateIterator.hasNext()) {
            log.info("No rebalanced dates found.");
            return;
        }

        Date currentDate = dateIterator.next();
        while (dateIterator.hasNext()) {
            Date nextDate = dateIterator.next();
            Date finalCurrentDate = currentDate; // Create a local final variable

            log.info("Processing Rebalanced Date: {}", finalCurrentDate);

            List<TopN_MomentumStock> topNStockListCurrentMonth = topNStockList.stream()
                    .filter(stock -> stock.getEndDate().equals(finalCurrentDate))
                    .toList();

            List<TopN_MomentumStock> topNStockListNextMonth = topNStockList.stream()
                    .filter(stock -> stock.getEndDate().equals(nextDate))
                    .toList();

            // Find the matching and non-matching stocks
            Map<String, List<TopN_MomentumStock>> listMatcherNonMatcherMap =
                    GenericMatchingUtility.findMatchesAndNonMatches(topNStockListCurrentMonth, topNStockListNextMonth, TopN_MomentumStock::getStockName);

            // Process the matching and non-matching stocks
            listMatcherNonMatcherMap.forEach((key, stockList) -> {
                switch (key) {
                    case "Matching" -> {
                        stockList.forEach(stock -> stock.setStockSignal(StockSignal.Hold));
                        topMomentumStockService.updateTopMomentumStock(stockList);
                    }
                    case "NotMatchingInList1" -> {
                        stockList.forEach(stock -> stock.setStockSignal(StockSignal.Sell));
                        topMomentumStockService.updateTopMomentumStock(stockList);
                    }
                    case "NotMatchingInList2" -> {
                        stockList.forEach(stock -> stock.setStockSignal(StockSignal.Buy));
                        topMomentumStockService.updateTopMomentumStock(stockList);
                    }
                    default -> log.warn("Unknown match category: {}", key);
                }
            });

            // Move to the next date
            currentDate = nextDate;
        }
    }
}
