package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.StockPriceCacheService;
import org.factor_investing.quant_strategy.model.*;
import org.factor_investing.quant_strategy.repository.MomentumStockBackrestRepository;
import org.factor_investing.quant_strategy.repository.StockPriceDataRepository;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.ReturnCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MomentumStockBackTestService {
    @Autowired
    private TopMomentumStockService topMomentumStockService;
    @Autowired
    private StockPriceDataRepository stockPriceDataRepository;
    @Autowired
    private StockPriceCacheService stockPriceCacheService;
    @Autowired
    private MomentumStockBackrestRepository momentumStockBacktestRepository;

    public void getBackTestingMomentumStrategyData(RebalenceStrategy rebalancedStrategy) {
        List<TopN_MomentumStock> topNStockList = topMomentumStockService.getMomentumStockByRebalenceStrategyBetweenRank(rebalancedStrategy);
        Set<LocalDate> allUniqueStockPriceDates = DateUtil.convertToLocalDateSet(stockPriceDataRepository.findDistinctByDate());

        // Get all unique rebalanced dates
        List<Date> distinctRebalancedDates = topNStockList.stream()
                .map(TopN_MomentumStock::getEndDate)
                .distinct()
                .collect(Collectors.toList());
        List<TopN_MomentumStock> returnCalculationList = new ArrayList<>();
        for (Date currentDate : distinctRebalancedDates) {
           // log.info("Processing Rebalanced Date: {}", currentDate);
            List<TopN_MomentumStock> topNStockListCurrentMonth = topNStockList.stream()
                    .filter(stock -> stock.getEndDate().equals(currentDate))
                    .toList();

            topNStockListCurrentMonth.forEach(stock -> {
                // Get stock price for the current date
                LocalDate previousMonthSellDate = DateUtil.convertDateToLocalDate(stock.getEndDate());
                LocalDate currentMonthBuyDate = DateUtil.getNextDate(allUniqueStockPriceDates, previousMonthSellDate);

                if (currentMonthBuyDate != null) {
                    int month = currentMonthBuyDate.getMonthValue();
                    int year = currentMonthBuyDate.getYear();
                    stock.setStrategyRunningMonth(currentMonthBuyDate.getMonth().name() + "-" + currentMonthBuyDate.getYear());
                    LocalDate sellDate = DateUtil.getEndMonthDate(year, month);
                    if (!allUniqueStockPriceDates.contains(sellDate)) {
                        sellDate = DateUtil.findNearestDate(allUniqueStockPriceDates, sellDate);
                    }
                    stock.setBuyDate(java.sql.Date.valueOf(currentMonthBuyDate));
                    stock.setSellDate(java.sql.Date.valueOf(sellDate));
                    float startDatePrice = stockPriceCacheService.getStockPrice(stock.getStockName(), stock.getBuyDate());
                    float endDatePrice = stockPriceCacheService.getStockPrice(stock.getStockName(), stock.getSellDate());
                    if (startDatePrice > 0 && endDatePrice > 0) {
                        stock.setBuyPrice(startDatePrice);
                        stock.setSellPrice(endDatePrice);
                    }
                    returnCalculationList.add(stock);
                }
            });
        }
        calculateReturns(returnCalculationList);
    }

    public void calculateReturns(List<TopN_MomentumStock> returnCalculationList) {
        List<SelectedMomentumStock> stockPerformanceList = new ArrayList<>();
        for (TopN_MomentumStock stock : returnCalculationList) {
            SelectedMomentumStock stockPerformance = new SelectedMomentumStock();
            stockPerformance.setStockName(stock.getStockName());
            stockPerformance.setRebalenceStrategy(stock.getRebalancedStrategy());
            stockPerformance.setStrategyRunningMonth(stock.getStrategyRunningMonth());

            stockPerformance.setBuyDate(stock.getBuyDate());
            stockPerformance.setSellDate(stock.getSellDate());
            stockPerformance.setBuyPrice(stock.getBuyPrice());
            stockPerformance.setSellPrice(stock.getSellPrice());

            float flatAmountPerStock = 20000;
            stockPerformance.setStockQuantity(ReturnCalculationUtils.getNumberOfStocks(flatAmountPerStock, stock.getBuyPrice()));

            stockPerformance.setInvestmentAmount(ReturnCalculationUtils.getAmountBasedOnStockNumber(stockPerformance.getStockQuantity(), stock.getBuyPrice()));
            stockPerformance.setSellAmount(ReturnCalculationUtils.getAmountBasedOnStockNumber(stockPerformance.getStockQuantity(), stock.getSellPrice()));

            stockPerformance.setProfitLoss(stockPerformance.getSellAmount() - stockPerformance.getInvestmentAmount());
            stockPerformance.setPercentageReturn(ReturnCalculationUtils.percentReturn(stockPerformance.getInvestmentAmount(), stockPerformance.getSellAmount()));
            stockPerformanceList.add(stockPerformance);
        }
        momentumStockBacktestRepository.saveAll(stockPerformanceList);
        stockPerformanceList.forEach(stockPerformance -> log.info("Stock Performance: {}", stockPerformance.getStockName()+ " "+stockPerformance.getStrategyRunningMonth()+" stock return:"+stockPerformance.getPercentageReturn()+"\n Profit/loss:"+stockPerformance.getProfitLoss()+"\n=============================="));
    }

}
