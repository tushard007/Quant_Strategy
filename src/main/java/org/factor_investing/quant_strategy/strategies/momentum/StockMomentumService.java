package org.factor_investing.quant_strategy.strategies.momentum;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.TopN_MomentumAssetType;
import org.factor_investing.quant_strategy.model.response.MomentumExecutionSummary;
import org.factor_investing.quant_strategy.model.response.SavedMomentumResult;
import org.factor_investing.quant_strategy.repository.TopMomentumStockRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.ReturnCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.factor_investing.quant_strategy.util.DateUtil.convertToLocalDateSet;

@Service
@Slf4j
public class StockMomentumService {

    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private StockPriceCacheService stockPriceCacheService;
    @Autowired
    private TopMomentumStockRepository topMomentumStockRepository;

    public List<MomentumExecutionSummary> getExecutionHistory(AssetDataType assetDataType) {
        Map<AssetDataType, Long> analyzedCounts = currentUniverseCounts();
        return topMomentumStockRepository.findAll().stream()
                .filter(item -> assetDataType == null || item.getAssetDataType() == assetDataType)
                .collect(Collectors.groupingBy(item -> Map.entry(item.getAssetDataType(), item.getStrategyRunDate())))
                .entrySet().stream()
                .map(entry -> new MomentumExecutionSummary(
                        entry.getKey().getKey(), entry.getKey().getValue(), entry.getValue().size(),
                        analyzedCounts.getOrDefault(entry.getKey().getKey(), 0L),
                        entry.getValue().stream().map(TopN_MomentumAssetType::getModificationDate)
                                .filter(Objects::nonNull).max(Date::compareTo).map(Date::toInstant).orElse(null)))
                .sorted(Comparator.comparing(MomentumExecutionSummary::strategyRunDate).reversed())
                .toList();
    }

    private Map<AssetDataType, Long> currentUniverseCounts() {
        Map<AssetDataType, Long> counts = new EnumMap<>(AssetDataType.class);
        counts.put(AssetDataType.STOCK, (long) stockPriceCacheService.getCachedAllStockPriceData().size());
        counts.put(AssetDataType.ETF, (long) stockPriceCacheService.getCachedAllETFPriceData().size());
        counts.put(AssetDataType.INDEX, (long) stockPriceCacheService.getCachedAllIndexPriceData().size());
        return counts;
    }

    public List<SavedMomentumResult> getSavedResults(AssetDataType assetDataType, java.sql.Date strategyRunDate) {
        return topMomentumStockRepository.findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(assetDataType, strategyRunDate)
                .stream().map(item -> new SavedMomentumResult(item.getStockName(), item.getPercentageReturn12Months(),
                        item.getPercentageReturn6Months(), item.getPercentageReturn3Months(), item.getStrategyRunDate(),
                        item.getRank12Months(), item.getRank6Months(), item.getRank3Months(), item.getTotalRankScore()))
                .toList();
    }

    /**
     * Runs the complete momentum workflow in the required order.
     * Rankings are never assigned when the initial calculation fails.
     */
    @Transactional
    public MomentumResult calculateAndRankMomentum(AssetDataType assetDataType, LocalDate asOfDate) {
        LocalDate calculationDate = asOfDate == null ? LocalDate.now(MARKET_TIME_ZONE) : asOfDate;
        MomentumResult calculation = calculateMomentum(assetDataType, calculationDate);
        if (!calculation.isValid()) {
            return calculation;
        }

        assignRanks(assetDataType, calculationDate);
        return new MomentumResult(
                calculation.getAllStocks(),
                calculation.getQualifiedStocks(),
                calculation.getTopStockNames(),
                true,
                "Momentum calculation and ranking completed successfully"
        );
    }

    /**
     * Calculate momentum for all stocks in the provided data
     *
     * @return MomentumResult containing all calculation results
     */
    public MomentumResult calculateMomentum(AssetDataType assetDataType, LocalDate asOfDate) {
        try {
            if (asOfDate.isAfter(LocalDate.now(MARKET_TIME_ZONE))) {
                throw new IllegalArgumentException("As-of date cannot be in the future");
            }
            log.info("Calculating {} momentum as of {}", assetDataType, asOfDate);
            Map<String, List<OHLCV>> stockData = null;
            if (AssetDataType.STOCK == assetDataType) {
                stockData = stockPriceCacheService.getCachedAllStockPriceData();
            }
            if (AssetDataType.ETF == assetDataType) {
                stockData = stockPriceCacheService.getCachedAllETFPriceData();
            }
            if (AssetDataType.INDEX == assetDataType) {
                stockData = stockPriceCacheService.getCachedAllIndexPriceData();
            }
            validateInput(stockData);

            List<StockMomentum> allResults = new ArrayList<>();
            List<TopN_MomentumAssetType> topN_momentumStocksList = new ArrayList<>();
            int count = 0;
            for (Map.Entry<String, List<OHLCV>> entry : stockData.entrySet()) {
                String stockName = entry.getKey();
                log.info("Calculating momentum for stock: {} ===========", stockName);
                List<OHLCV> ohlcData = entry.getValue();
                count++;
                try {
                    StockMomentum momentum = calculateStockMomentum(stockName, ohlcData, assetDataType, asOfDate);
                    if (momentum != null) {
                        allResults.add(momentum);
                        if (momentum.isQualifiesForMomentum()) {
                            TopN_MomentumAssetType topN_momentumStock = new TopN_MomentumAssetType();
                            topN_momentumStock.setAssetDataType(assetDataType);
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
                    System.err.println("Error calculating momentum for " + stockName + ": " + e.getMessage());
                }
                log.info("Calculation in progress remaining stock to process: {}", stockData.size() - count);
            }
            if (allResults.isEmpty()) {
                return new MomentumResult(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), false,
                        "No assets have complete 12-month, 6-month and 3-month price history on or before "
                                + asOfDate + ". Select a later date or load older price data.");
            }
            java.sql.Date strategyRunDate = java.sql.Date.valueOf(asOfDate);
            topMomentumStockRepository.deleteByAssetDataTypeAndStrategyRunDate(assetDataType, strategyRunDate);
            topMomentumStockRepository.flush();
            if (!topN_momentumStocksList.isEmpty()) {
                topMomentumStockRepository.saveAll(topN_momentumStocksList);
            }
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
     * Calculate momentum for a single stock
     */
    private StockMomentum calculateStockMomentum(String stockName, List<OHLCV> ohlcData,
                                                  AssetDataType assetDataType, LocalDate asOfDate) {
        if (ohlcData == null || ohlcData.isEmpty()) {
            return null;
        }
        if (ohlcData.size() > MomentumConstants.MIN_DATA_POINTS) {

            Set<Date> allUniqueStockPrice = stockPriceCacheService.getAllAssetWisePriceDateBySymbol(stockName,assetDataType);
            Set<LocalDate> allUniqueStockPriceLocaleDates = convertToLocalDateSet(allUniqueStockPrice);

            LocalDate currentDate = DateUtil.findNearestPastDate(allUniqueStockPriceLocaleDates, asOfDate);
            LocalDate previous1YearDate = DateUtil.findNearestPastDate(
                    allUniqueStockPriceLocaleDates, DateUtil.getDateBeforeYear(asOfDate, 1));
            LocalDate previous6MonthDate = DateUtil.findNearestPastDate(
                    allUniqueStockPriceLocaleDates, DateUtil.getDateBeforeMonth(asOfDate, 6));
            LocalDate previous3MonthDate = DateUtil.findNearestPastDate(
                    allUniqueStockPriceLocaleDates, DateUtil.getDateBeforeMonth(asOfDate, 3));

            if (currentDate == null || previous1YearDate == null
                    || previous6MonthDate == null || previous3MonthDate == null) {
                return null;
            }
            // Fetch prices for the required dates
            Double currentPrice = getMostRecentPrice(stockName, currentDate,assetDataType);
            Double previous1YearPrice = getMostRecentPrice(stockName, previous1YearDate,assetDataType);
            Double previous6MonthPrice = getMostRecentPrice(stockName, previous6MonthDate,assetDataType);
            Double previous3MonthPrice = getMostRecentPrice(stockName, previous3MonthDate,assetDataType);

            if (currentPrice == null || previous1YearPrice == null || previous6MonthPrice == null || previous3MonthPrice == null) {
                return null;
            } else {
                log.info("==============/n currentPrice:{}, previous1YearPrice:{}, previous6MonthPrice:{}, previous3MonthPrice:{}", currentPrice, previous1YearPrice, previous6MonthPrice, previous3MonthPrice);
            }

            // Calculate returns for different periods
            Float oneYearReturn = ReturnCalculationUtils.percentReturn(previous1YearPrice.floatValue(), currentPrice.floatValue());
            Float sixMonthReturn = ReturnCalculationUtils.percentReturn(previous6MonthPrice.floatValue(), currentPrice.floatValue());
            Float threeMonthReturn = ReturnCalculationUtils.percentReturn(previous3MonthPrice.floatValue(), currentPrice.floatValue());
            log.info("oneYearReturn:{}, sixMonthReturn:{}, threeMonthReturn:{}\n====================",
                    oneYearReturn, sixMonthReturn, threeMonthReturn);
            return new StockMomentum(stockName, oneYearReturn, sixMonthReturn, threeMonthReturn, asOfDate);
        } else {
            log.error(
                    "Insufficient data points for {}. Required: {}, Provided: {}",
                    stockName,
                    MomentumConstants.MIN_DATA_POINTS,
                    ohlcData.size()
            );
            return null;
        }
    }

    /**
     * Get the most recent price data
     */
    private double getMostRecentPrice(String stockName, LocalDate priceDate,AssetDataType assetDataType) {
        return stockPriceCacheService.getStockClosingPriceBySymbolAndDate(stockName, priceDate,assetDataType);
    }

    public void assignRanks_old(AssetDataType assetDataType) {
        List<TopN_MomentumAssetType> momentumAssypeList = topMomentumStockRepository.findAll();
        if(AssetDataType.STOCK==assetDataType) {
            momentumAssypeList = momentumAssypeList.stream().filter(stock -> stock.getAssetDataType() == AssetDataType.STOCK).collect(Collectors.toList());
        }
        if(AssetDataType.ETF ==assetDataType) {
            momentumAssypeList = momentumAssypeList.stream().filter(stock -> stock.getAssetDataType() == AssetDataType.ETF).collect(Collectors.toList());
        }
        if(AssetDataType.INDEX ==assetDataType) {
            momentumAssypeList = momentumAssypeList.stream().filter(stock -> stock.getAssetDataType() == AssetDataType.INDEX).collect(Collectors.toList());
        }
        // Rank by 12 months return
        rankMomentumAsset(momentumAssypeList, Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn12Months).reversed(),
                TopN_MomentumAssetType::setRank12Months);

        // Rank by 6 months return
        rankMomentumAsset(momentumAssypeList, Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn6Months).reversed(),
                TopN_MomentumAssetType::setRank6Months);

        // Rank by 3 months return
        rankMomentumAsset(momentumAssypeList, Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn3Months).reversed(),
                TopN_MomentumAssetType::setRank3Months);

        // Calculate total rank score (lower is better since rank 1 is top)
        momentumAssypeList.forEach(stock -> {
            int totalRank = stock.getRank12Months() + stock.getRank6Months() + stock.getRank3Months();
            stock.setTotalRankScore(totalRank);
        });
        // Save updated ranks back to the database
        topMomentumStockRepository.saveAll(momentumAssypeList);
        log.info("Momentum rankings updated successfully.");
    }

    private void rankMomentumAsset(List<TopN_MomentumAssetType> momentumAssypeList,
                                   Comparator<TopN_MomentumAssetType> comparator,
                                   BiConsumer<TopN_MomentumAssetType, Integer> rankSetter) {
        // Sort + assign ranks (1-based)
        List<TopN_MomentumAssetType> sorted = momentumAssypeList.stream()
                .sorted(comparator)
                .toList();

        IntStream.range(0, sorted.size())
                .forEach(i -> rankSetter.accept(sorted.get(i), i + 1));
    }
    
    public void assignRanks(AssetDataType assetDataType, LocalDate asOfDate) {
        List<TopN_MomentumAssetType> momentumAssetList =
                topMomentumStockRepository.findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(
                        assetDataType, java.sql.Date.valueOf(asOfDate));

        // Rank by 12 months return (1 = highest return)
        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn12Months).reversed(),
                TopN_MomentumAssetType::setRank12Months);

        // Rank by 6 months return
        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn6Months).reversed(),
                TopN_MomentumAssetType::setRank6Months);

        // Rank by 3 months return
        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_MomentumAssetType::getPercentageReturn3Months).reversed(),
                TopN_MomentumAssetType::setRank3Months);

        // === WEIGHTED TOTAL RANK SCORE (higher weight on 3-month) ===
        final int WEIGHT_12M = 1;
        final int WEIGHT_6M  = 2;
        final int WEIGHT_3M  = 3;

        momentumAssetList.forEach(stock -> {
            int totalRank = stock.getRank12Months() * WEIGHT_12M
                    + stock.getRank6Months()  * WEIGHT_6M
                    + stock.getRank3Months()  * WEIGHT_3M;
            stock.setTotalRankScore(totalRank);
        });

        // Save updated ranks back to the database
        topMomentumStockRepository.saveAll(momentumAssetList);
        log.info("Momentum rankings updated successfully (weighted: 3m>6m>12m).");
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
