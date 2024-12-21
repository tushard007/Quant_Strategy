package org.factor_investing.quant_strategy.momentum.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.StockPriceCacheService;
import org.factor_investing.quant_strategy.repository.StockPriceDataRepository;
import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.repository.TopMomentumStockRepository;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.ReturnCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


import static org.factor_investing.quant_strategy.util.DateUtil.convertToLocalDateSet;

@Slf4j
@Service
public class TopMomentumStockService {

    @Autowired
    private StockPriceDataRepository stockPriceDataRepository;
    @Autowired
    private TopMomentumStockRepository topMomentumStockRepository;
    @Autowired
    private StockPriceCacheService stockPriceCacheService;

    public void runTopMomentumStocksForPeriod(RebalenceStrategy rebalenceStrategy, java.sql.Date startDate, java.sql.Date endDate) {
        Set<java.sql.Date> eachRebalencedDate = new HashSet<>();
        if (RebalenceStrategy.Weekly.equals(rebalenceStrategy)) {
            eachRebalencedDate = stockPriceDataRepository.findDistinctBetweenDateOfEachFriday(startDate, endDate);
            log.info("Each Friday Date:{}", eachRebalencedDate + "Date count" + eachRebalencedDate.size() + " Rebalanced Strategy: " + rebalenceStrategy);
        }
        if (RebalenceStrategy.Monthly.equals(rebalenceStrategy)) {
            eachRebalencedDate = stockPriceDataRepository.findDistinctLastDateOfEachMonth(startDate, endDate);
            log.info("Each Month Date:{}", eachRebalencedDate + "Date count" + eachRebalencedDate.size() + " Rebalanced Strategy: " + rebalenceStrategy);
        }

        if (!eachRebalencedDate.isEmpty()) {
            eachRebalencedDate.add(endDate);
            for (Date rebalancedDate : eachRebalencedDate) {
                CalculateStockReturnForYear(rebalancedDate, rebalenceStrategy);
            }
        }
    }

    //Calculate past 1y,6m,3m stock return and assign rank based on yearly return in asc order
    public List<TopN_MomentumStock> CalculateStockReturnForYear(Date rebalenceDate, RebalenceStrategy rebalenceStrategy) {

        //Getting All Unique stock price date from database
        Set<Date> allUniqueStockPriceDates = stockPriceDataRepository.findDistinctByPriceDate();
        Set<String> allUniqueStockTicker = stockPriceDataRepository.findDistinctByStockTicker();
        Set<LocalDate> allUniqueStockPriceLocaleDates = convertToLocalDateSet(allUniqueStockPriceDates);

        LocalDate endDate = DateUtil.convertDateToLocalDate(rebalenceDate);
        if (!allUniqueStockPriceLocaleDates.contains(endDate)) {
            endDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, endDate);
        }
        //start date for calculating stock return for past one year from rebalanced date(end date)
        LocalDate startDate = DateUtil.getDateBeforeMonth(endDate, 12);
        if (!allUniqueStockPriceLocaleDates.contains(startDate)) {
            startDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, startDate);
        }
        LocalDate previous6MonthDate = DateUtil.getDateBeforeMonth(endDate, 6);
        if (!allUniqueStockPriceLocaleDates.contains(previous6MonthDate)) {
            previous6MonthDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, previous6MonthDate);
        }
        LocalDate previous3MonthDate = DateUtil.getDateBeforeMonth(endDate, 3);
        if (!allUniqueStockPriceLocaleDates.contains(previous3MonthDate)) {
            previous3MonthDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, previous3MonthDate);
        }

        List<TopN_MomentumStock> objStockMomentumList = new ArrayList<>();
        //iterating map and fetching stock price for start date and end date
        // calculating percentage return for all stocks
        List<TopN_MomentumStock> highestReturnSMList = null;
        for (String stockName : allUniqueStockTicker) {
            TopN_MomentumStock topNMomentumStock = new TopN_MomentumStock();
            topNMomentumStock.setStockName(stockName);
            topNMomentumStock.setRebalancedStrategy(rebalenceStrategy);
            log.info("\n=======Stock Name:{} ============================", stockName);

            float startDatePrice = stockPriceCacheService.getStockPrice(stockName, java.sql.Date.valueOf(startDate));
            if (startDatePrice > 0) {
                topNMomentumStock.setStartDate(java.sql.Date.valueOf(startDate));
                topNMomentumStock.setStartDateStockPrice(startDatePrice);
            }

            float endDatePrice = stockPriceCacheService.getStockPrice(stockName, java.sql.Date.valueOf(endDate));

            if (endDatePrice > 0) {
                topNMomentumStock.setEndDate(java.sql.Date.valueOf(endDate));
                topNMomentumStock.setEndDateStockPrice(endDatePrice);
            }
            if (startDatePrice > 0 && endDatePrice > 0) {

                float priceData3MonthAgo = stockPriceCacheService.getStockPrice(stockName, java.sql.Date.valueOf(previous3MonthDate));
                if (priceData3MonthAgo > 0) {
                    float return3Month = ReturnCalculationUtils.percentReturn(priceData3MonthAgo, endDatePrice);
                    topNMomentumStock.setPercentageReturn3Months(return3Month);
                    log.debug("\n 3 Month Return{} \n", return3Month);
                }
                float priceData6MonthAgo = stockPriceCacheService.getStockPrice(stockName, java.sql.Date.valueOf(previous6MonthDate));
                if (priceData6MonthAgo > 0) {
                    float return6Month = ReturnCalculationUtils.percentReturn(priceData6MonthAgo, endDatePrice);
                    topNMomentumStock.setPercentageReturn6Months(return6Month);
                    log.debug("\n 6 Month Return{} \n", return6Month);
                }

                topNMomentumStock.setPercentageReturn12Months(ReturnCalculationUtils.percentReturn(startDatePrice, endDatePrice));
                log.debug("\n Start Stock Date:{} Start Price:{}", topNMomentumStock.getStartDate(), topNMomentumStock.getStartDateStockPrice());
                log.debug("\n End Date:{}end Price:{}", topNMomentumStock.getEndDate(), topNMomentumStock.getEndDateStockPrice());
                log.debug("\n Annual Return{} \n", topNMomentumStock.getPercentageReturn12Months());
                if (topNMomentumStock.getPercentageReturn12Months() > 0 &&
                        topNMomentumStock.getPercentageReturn6Months() > 0 &&
                        topNMomentumStock.getPercentageReturn3Months() > 0) {
                    objStockMomentumList.add(topNMomentumStock);
                }

            }
        }
        highestReturnSMList = objStockMomentumList
                .stream()
                .sorted(Comparator.comparing(TopN_MomentumStock::getPercentageReturn12Months).reversed())
                .toList();

        List<TopN_MomentumStock> finalHighestReturnSMList = highestReturnSMList;
        IntStream.range(0, highestReturnSMList.size())
                .forEach(i -> finalHighestReturnSMList.get(i).setRank(i + 1));
        highestReturnSMList = finalHighestReturnSMList.stream().limit(50).toList();
        topMomentumStockRepository.saveAll(highestReturnSMList);
        return finalHighestReturnSMList;
    }

    public List<TopN_MomentumStock> getAllTopMomentumStockByRebalenceStrategy(RebalenceStrategy rebalancedStrategy) {
        return topMomentumStockRepository.findAllByRebalancedStrategy(rebalancedStrategy.toString());
    }

    public Map<Date, List<TopN_MomentumStock>> getTopNStockGroupByRebalencedDate(RebalenceStrategy rebalancedStrategy) {
        Map<Date, List<TopN_MomentumStock>> topNStockGroupByRebalenceDdate=new LinkedHashMap<>();
        topNStockGroupByRebalenceDdate = getAllTopMomentumStockByRebalenceStrategy(rebalancedStrategy).stream()
                .sorted(Comparator.comparing(TopN_MomentumStock::getEndDate)) // Ensure ordering by date
                .collect(Collectors.groupingBy(
                        TopN_MomentumStock::getEndDate, // Key mapper: use endDate as the key
                        LinkedHashMap::new,             // Ensure insertion order with LinkedHashMap
                        Collectors.toList()             // Value mapper: list of stocks
                ));
        topNStockGroupByRebalenceDdate.forEach((date, stocks) -> {
            System.out.println("\n=======Rebalance Date: " + date);
            stocks.forEach(stock -> System.out.println("  Stock: " + stock.getStockName() +
                    "  Rank: " + stock.getRank() + "  Annual Return: " + stock.getPercentageReturn12Months()));
        });
        return topNStockGroupByRebalenceDdate;
    }

}



