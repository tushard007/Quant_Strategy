package org.factor_investing.quant_strategy.strategies.momentum;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.ReturnCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.factor_investing.quant_strategy.util.DateUtil.convertToLocalDateSet;

@Service
@Slf4j
public class StockMomentumService {

    @Autowired
    private StockPriceCacheService stockPriceCacheService;

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
                    }
                } catch (Exception e) {
                    // Log error but continue with other stocks
                    System.err.println(STR."Error calculating momentum for \{stockName}: \{e.getMessage()}");
                }
                log.info("Calculation in progress remaining stock to process: {}", stockData.size()-count);
            }

            // Sort by 1-year return (descending)
            List<StockMomentum> sortedResults = allResults.stream()
                    .sorted(Comparator.comparingDouble(StockMomentum::getOneYearReturn).reversed())
                    .collect(Collectors.toList());

            // Filter qualified stocks
            List<StockMomentum> qualifiedStocks = sortedResults.stream()
                    .filter(StockMomentum::qualifiesForMomentum)
                    .collect(Collectors.toList());

            // Get top stock names
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
                log.info("\n********** currentPrice:{}, previous1YearPrice:{}, previous6MonthPrice:{}, previous3MonthPrice:{}", currentPrice, previous1YearPrice, previous6MonthPrice, previous3MonthPrice);
            }

            // Calculate returns for different periods
            Float oneYearReturn = ReturnCalculationUtils.percentReturn(previous1YearPrice.floatValue(), currentPrice.floatValue());
            Float sixMonthReturn = ReturnCalculationUtils.percentReturn(previous6MonthPrice.floatValue(), currentPrice.floatValue());
            Float threeMonthReturn = ReturnCalculationUtils.percentReturn(previous3MonthPrice.floatValue(), currentPrice.floatValue());
            if (oneYearReturn > 0 && sixMonthReturn > 0 && threeMonthReturn > 0) {
                log.info("oneYearReturn:{}, sixMonthReturn:{}, threeMonthReturn:{}\n************", oneYearReturn, sixMonthReturn, threeMonthReturn);
            }

            return new StockMomentum(stockName, oneYearReturn, sixMonthReturn, threeMonthReturn);
        } else {
            log.error(STR."Insufficient data points for \{stockName}. Required: \{MomentumConstants.MIN_DATA_POINTS}, Provided: \{ohlcData.size()}");
            return null;
        }

    }


    /**
     * Get the most recent price data
     */
    private double getMostRecentPrice(String stockName, LocalDate priceDate) {
        return stockPriceCacheService.getStockClosingPriceBySymbolAndDate(stockName, priceDate);

    }

    private void validateInput(Map<String, List<OHLCV>> stockData) {
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
