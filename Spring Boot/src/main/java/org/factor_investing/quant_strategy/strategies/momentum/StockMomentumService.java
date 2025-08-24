package org.factor_investing.quant_strategy.strategies.momentum;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.repository.TopMomentumStockRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.ReturnCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.factor_investing.quant_strategy.util.DateUtil.convertToLocalDateSet;

@Service
@Slf4j
public class StockMomentumService {

    @Autowired
    private StockPriceCacheService stockPriceCacheService;
    @Autowired
    private TopMomentumStockRepository topMomentumStockRepository;

    /**
     * Calculate momentum for all stocks in the provided data
     *
     * @return MomentumResult containing all calculation results
     */
    public MomentumResult calculateMomentum() {
        try {
            Map<String, List<OHLCV>> stockData = stockPriceCacheService.getAllStockPriceData();
            validateInput(stockData);

            List<StockMomentum> allResults = new ArrayList<>();
            List<TopN_MomentumStock> topN_momentumStocksList = new ArrayList<>();
            int count = 0;
            for (Map.Entry<String, List<OHLCV>> entry : stockData.entrySet()) {
                String stockName = entry.getKey();
                log.info("Calculating momentum for stock: {} ===========", stockName);
                List<OHLCV> ohlcData = entry.getValue();
                count++;
                try {
                    StockMomentum momentum = calculateStockMomentum(stockName, ohlcData);
                    if (momentum != null) {
                        allResults.add(momentum);
                        if (momentum.isQualifiesForMomentum()) {
                            TopN_MomentumStock topN_momentumStock = new TopN_MomentumStock();
                            topN_momentumStock.setStockName(momentum.getStockName());
                            topN_momentumStock.setPercentageReturn12Months(momentum.getOneYearReturn());
                            topN_momentumStock.setPercentageReturn6Months(momentum.getSixMonthReturn());
                            topN_momentumStock.setPercentageReturn3Months(momentum.getThreeMonthReturn());
                            topN_momentumStock.setStrategyRunDate(java.sql.Date.valueOf(momentum.getStrategyRunDate()));
                            topN_momentumStocksList.add(topN_momentumStock);
                        }
                        log.info("Calculated momentum: {}", momentum);
                    }
                } catch (Exception e) {
                    // Log error but continue with other stocks
                    System.err.println(STR."Error calculating momentum for \{stockName}: \{e.getMessage()}");
                }
                log.info("Calculation in progress remaining stock to process: {}", stockData.size() - count);
            }
            if (!topN_momentumStocksList.isEmpty())
                topMomentumStockRepository.saveAll(topN_momentumStocksList);
            // Sort by 1-year return (descending)
            List<StockMomentum> sortedResults = allResults.stream()
                    .sorted(Comparator.comparingDouble(StockMomentum::getOneYearReturn).reversed())
                    .collect(Collectors.toList());

            // Filter qualified stocks
            List<StockMomentum> qualifiedStocks = sortedResults.stream()
                    .filter(StockMomentum::isQualifiesForMomentum)
                    .collect(Collectors.toList());

            // Get top stock names...TODO:modify after full implementation based on TotalRanking
            List<String> topStockNames = qualifiedStocks.stream()
                    .limit(MomentumConstants.TOP_NUMBER_MOMENTUM_STOCKS)
                    .map(StockMomentum::getStockName)
                    .collect(Collectors.toList());

            return new MomentumResult(sortedResults, qualifiedStocks, topStockNames,
                    true, "Momentum calculation successful");

        } catch (Exception e) {
            return new MomentumResult(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), false, e.getMessage());
        }
    }


    /**
     * Get only momentum stock names
     */
    public List<String> getMomentumStocksList() {

        MomentumResult result = calculateMomentum();
        return result.getTopStockNames();
    }

    /**
     * Calculate momentum for a single stock
     */
    private StockMomentum calculateStockMomentum(String stockName, List<OHLCV> ohlcData) {
        if (ohlcData == null || ohlcData.isEmpty()) {
            return null;
        }
        if (ohlcData.size() > MomentumConstants.MIN_DATA_POINTS) {

            Set<Date> allUniqueStockPrice = stockPriceCacheService.getAllStockPriceDateBySymbol(stockName);
            Set<LocalDate> allUniqueStockPriceLocaleDates = convertToLocalDateSet(allUniqueStockPrice);

            LocalDate currentDate = LocalDate.now();
            if (!allUniqueStockPriceLocaleDates.contains(currentDate)) {
                currentDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, currentDate);
            }
            LocalDate previous1YearDate = DateUtil.getDateBeforeYear(currentDate, 1);
            if (!allUniqueStockPriceLocaleDates.contains(previous1YearDate)) {
                previous1YearDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, previous1YearDate);
            }
            LocalDate previous6MonthDate = DateUtil.getDateBeforeMonth(currentDate, 6);
            if (!allUniqueStockPriceLocaleDates.contains(previous6MonthDate)) {
                previous6MonthDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, previous6MonthDate);
            }
            LocalDate previous3MonthDate = DateUtil.getDateBeforeMonth(currentDate, 3);
            if (!allUniqueStockPriceLocaleDates.contains(previous3MonthDate)) {
                previous3MonthDate = DateUtil.findNearestDate(allUniqueStockPriceLocaleDates, previous3MonthDate);
            }
            // Fetch prices for the required dates
            Double currentPrice = getMostRecentPrice(stockName, currentDate);
            Double previous1YearPrice = getMostRecentPrice(stockName, previous1YearDate);
            Double previous6MonthPrice = getMostRecentPrice(stockName, previous6MonthDate);
            Double previous3MonthPrice = getMostRecentPrice(stockName, previous3MonthDate);

            if (currentPrice == null || previous1YearPrice == null || previous6MonthPrice == null || previous3MonthPrice == null) {
                return null;
            } else {
                log.info("==============/n currentPrice:{}, previous1YearPrice:{}, previous6MonthPrice:{}, previous3MonthPrice:{}", currentPrice, previous1YearPrice, previous6MonthPrice, previous3MonthPrice);
            }

            // Calculate returns for different periods
            Float oneYearReturn = ReturnCalculationUtils.percentReturn(previous1YearPrice.floatValue(), currentPrice.floatValue());
            Float sixMonthReturn = ReturnCalculationUtils.percentReturn(previous6MonthPrice.floatValue(), currentPrice.floatValue());
            Float threeMonthReturn = ReturnCalculationUtils.percentReturn(previous3MonthPrice.floatValue(), currentPrice.floatValue());
            if (oneYearReturn > 0 && sixMonthReturn > 0 && threeMonthReturn > 0) {
                log.info("oneYearReturn:{}, sixMonthReturn:{}, threeMonthReturn:{}\n====================", oneYearReturn, sixMonthReturn, threeMonthReturn);
                return new StockMomentum(stockName, oneYearReturn, sixMonthReturn, threeMonthReturn, currentDate);
            }
            else {
                return null;
            }
        } else {
                log.error(STR."Insufficient data points for \{stockName}. Required: \{MomentumConstants.MIN_DATA_POINTS}, Provided: \{ohlcData.size()}");
                return null;
            }
        }

        /**
         * Get the most recent price data
         */
        private double getMostRecentPrice (String stockName, LocalDate priceDate){
            return stockPriceCacheService.getStockClosingPriceBySymbolAndDate(stockName, priceDate);
        }

        public void assignRanks () {
            List<TopN_MomentumStock> stocks = topMomentumStockRepository.findAll();
            // Rank by 12 months return
            rankStocks(stocks, Comparator.comparing(TopN_MomentumStock::getPercentageReturn12Months).reversed(),
                    TopN_MomentumStock::setRank12Months);

            // Rank by 6 months return
            rankStocks(stocks, Comparator.comparing(TopN_MomentumStock::getPercentageReturn6Months).reversed(),
                    TopN_MomentumStock::setRank6Months);

            // Rank by 3 months return
            rankStocks(stocks, Comparator.comparing(TopN_MomentumStock::getPercentageReturn3Months).reversed(),
                    TopN_MomentumStock::setRank3Months);

            // Calculate total rank score (lower is better since rank 1 is top)
            stocks.forEach(stock -> {
                int totalRank = stock.getRank12Months() + stock.getRank6Months() + stock.getRank3Months();
                stock.setTotalRankScore(totalRank);
            });
            // Save updated ranks back to the database
            topMomentumStockRepository.saveAll(stocks);
            log.info("Momentum rankings updated successfully.");
        }

        private void rankStocks (List < TopN_MomentumStock > stocks,
                Comparator < TopN_MomentumStock > comparator,
                BiConsumer < TopN_MomentumStock, Integer > rankSetter){
            // Sort + assign ranks (1-based)
            List<TopN_MomentumStock> sorted = stocks.stream()
                    .sorted(comparator)
                    .toList();

            IntStream.range(0, sorted.size())
                    .forEach(i -> rankSetter.accept(sorted.get(i), i + 1));
        }

        private void validateInput (Map < String, List < OHLCV >> stockData){
            if (stockData == null || stockData.isEmpty()) {
                throw new IllegalArgumentException("Stock data cannot be null or empty");
            }
            for (Map.Entry<String, List<OHLCV>> entry : stockData.entrySet()) {
                String stockName = entry.getKey();
                List<OHLCV> ohlcvList = entry.getValue();

                if (stockName == null || stockName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Stock name cannot be null or empty");
                }
            }
        }
    }
