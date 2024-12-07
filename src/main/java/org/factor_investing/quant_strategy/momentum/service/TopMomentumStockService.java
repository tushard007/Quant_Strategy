package org.factor_investing.quant_strategy.momentum.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.StockPriceData;
import org.factor_investing.quant_strategy.StockPriceDataRepository;
import org.factor_investing.quant_strategy.momentum.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.momentum.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.momentum.repository.TopMomentumStockRepository;
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

    //Calculate past 1 year stock return
    public List<TopN_MomentumStock> CalculateStockReturnForYear(Date rebalenceDate, RebalenceStrategy rebalenceStrategy) throws InterruptedException {

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
        List<TopN_MomentumStock> objStockMomentumList = new ArrayList<>();
        //iterating map and fetching stock price for start date and end date
        // calculating percentage return for all stocks
        for (String stockName : allUniqueStockTicker) {
            TopN_MomentumStock topNMomentumStock = new TopN_MomentumStock();
            topNMomentumStock.setStockName(stockName);
            topNMomentumStock.setRebalancedStrategy(rebalenceStrategy);
            log.info("\nStock Name:" + stockName + " ============================");

            StockPriceData priceDataStartDate = stockPriceDataRepository.findByStockTickerAndPriceDate(stockName, java.sql.Date.valueOf(startDate));
            if (priceDataStartDate != null) {
                topNMomentumStock.setStartDate(java.sql.Date.valueOf(startDate));
                topNMomentumStock.setStartDateStockPrice(priceDataStartDate.getStockPrice());
            }
            StockPriceData priceDataEndDate = stockPriceDataRepository.findByStockTickerAndPriceDate(stockName, java.sql.Date.valueOf(endDate));
            if (priceDataEndDate != null) {
                topNMomentumStock.setEndDate(java.sql.Date.valueOf(endDate));
                topNMomentumStock.setEndDateStockPrice(priceDataEndDate.getStockPrice());
            }
            if (priceDataStartDate != null && priceDataEndDate != null
                    && priceDataStartDate.getStockPrice() > 0 && priceDataEndDate.getStockPrice() > 0) {
                topNMomentumStock.setPercentageReturn(ReturnCalculationUtils.percentReturn(priceDataStartDate.getStockPrice(), priceDataEndDate.getStockPrice()));
                log.info("\n Start Stock Date:" + topNMomentumStock.getStartDate() + " Start Price:" + topNMomentumStock.getStartDateStockPrice());
                log.info("\n End Date:" + topNMomentumStock.getEndDate() + "end Price:" + topNMomentumStock.getEndDateStockPrice());
                log.info("\n Annual Return" + topNMomentumStock.getPercentageReturn() + " \n---------------------------");
               if(topNMomentumStock.getPercentageReturn() > 0) {
                   objStockMomentumList.add(topNMomentumStock);
               }

            }
        }
        List<TopN_MomentumStock> highestReturnSMList = new ArrayList<>();
        highestReturnSMList = objStockMomentumList
                .stream()
                .sorted(Comparator.comparing(TopN_MomentumStock::getPercentageReturn).reversed())
                .toList();

        List<TopN_MomentumStock> finalHighestReturnSMList = highestReturnSMList;
        IntStream.range(0, highestReturnSMList.size())
                .forEach(i -> finalHighestReturnSMList.get(i).setRank(i + 1));

        topMomentumStockRepository.saveAll(finalHighestReturnSMList);
        return finalHighestReturnSMList;
    }
}

