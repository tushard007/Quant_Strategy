package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.TopN_RiskAdjustedMomentumAssetType;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumExecutionSummary;
import org.factor_investing.quant_strategy.model.response.SavedRiskAdjustedMomentumResult;
import org.factor_investing.quant_strategy.repository.TopRiskAdjustedMomentumStockRepository;
import org.factor_investing.quant_strategy.service.ETFMasterDataService;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Parallel, independent counterpart to StockMomentumService. Differs from the original in three ways:
 * 1) the 12-month leg skips the most recent month (classic "12-1" momentum, avoids short-term reversal noise),
 * 2) the ETF universe excludes ETFs flagged as commodity (isCommodity) before ranking,
 * 3) each qualified asset carries a trailing-volatility figure used for inverse-volatility position weighting.
 */
@Service
@Slf4j
public class RiskAdjustedMomentumService {

    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private StockPriceCacheService stockPriceCacheService;
    @Autowired
    private TopRiskAdjustedMomentumStockRepository topRiskAdjustedMomentumStockRepository;
    @Autowired
    private ETFMasterDataService etfMasterDataService;

    public List<RiskAdjustedMomentumExecutionSummary> getExecutionHistory(AssetDataType assetDataType) {
        Map<AssetDataType, Long> analyzedCounts = currentUniverseCounts();
        return topRiskAdjustedMomentumStockRepository.findAll().stream()
                .filter(item -> assetDataType == null || item.getAssetDataType() == assetDataType)
                .collect(Collectors.groupingBy(item -> Map.entry(item.getAssetDataType(), item.getStrategyRunDate())))
                .entrySet().stream()
                .map(entry -> new RiskAdjustedMomentumExecutionSummary(
                        entry.getKey().getKey(), entry.getKey().getValue(), entry.getValue().size(),
                        analyzedCounts.getOrDefault(entry.getKey().getKey(), 0L),
                        entry.getValue().stream().map(TopN_RiskAdjustedMomentumAssetType::getModificationDate)
                                .filter(Objects::nonNull).max(Date::compareTo).map(Date::toInstant).orElse(null)))
                .sorted(Comparator.comparing(RiskAdjustedMomentumExecutionSummary::strategyRunDate).reversed())
                .toList();
    }

    private Map<AssetDataType, Long> currentUniverseCounts() {
        Map<AssetDataType, Long> counts = new EnumMap<>(AssetDataType.class);
        counts.put(AssetDataType.STOCK, (long) stockPriceCacheService.getCachedAllStockPriceData().size());
        counts.put(AssetDataType.ETF, (long) nonCommodityEtfPriceData().size());
        counts.put(AssetDataType.INDEX, (long) stockPriceCacheService.getCachedAllIndexPriceData().size());
        return counts;
    }

    private Map<String, List<OHLCV>> universeRawData(AssetDataType assetDataType) {
        if (AssetDataType.STOCK == assetDataType) return stockPriceCacheService.getCachedAllStockPriceData();
        if (AssetDataType.ETF == assetDataType) return nonCommodityEtfPriceData();
        if (AssetDataType.INDEX == assetDataType) return stockPriceCacheService.getCachedAllIndexPriceData();
        return null;
    }

    /** ETF cache filtered to exclude symbols flagged isCommodity=true in the ETF master data. */
    private Map<String, List<OHLCV>> nonCommodityEtfPriceData() {
        Set<String> commoditySymbols = etfMasterDataService.search("").stream()
                .filter(etf -> Boolean.TRUE.equals(etf.getIsCommodity()))
                .map(NSE_ETFMasterData::getSymbol)
                .collect(Collectors.toSet());
        Map<String, List<OHLCV>> etfData = stockPriceCacheService.getCachedAllETFPriceData();
        if (commoditySymbols.isEmpty()) return etfData;
        return etfData.entrySet().stream()
                .filter(entry -> !commoditySymbols.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<SavedRiskAdjustedMomentumResult> getSavedResults(AssetDataType assetDataType, java.sql.Date strategyRunDate) {
        return topRiskAdjustedMomentumStockRepository.findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(assetDataType, strategyRunDate)
                .stream()
                .sorted(Comparator.comparingInt(TopN_RiskAdjustedMomentumAssetType::getTotalRankScore)
                        .thenComparing(TopN_RiskAdjustedMomentumAssetType::getStockName))
                .map(item -> new SavedRiskAdjustedMomentumResult(item.getStockName(), item.getPercentageReturn12Months(),
                        item.getPercentageReturn6Months(), item.getPercentageReturn3Months(), item.getVolatility(),
                        item.getInverseVolWeight(), item.getStrategyRunDate(),
                        item.getRank12Months(), item.getRank6Months(), item.getRank3Months(), item.getTotalRankScore()))
                .toList();
    }

    @Transactional
    public RiskAdjustedMomentumResult calculateAndRankMomentum(AssetDataType assetDataType, LocalDate asOfDate) {
        return calculateAndRankMomentum(assetDataType, asOfDate, null, null, null);
    }

    @Transactional
    public RiskAdjustedMomentumResult calculateAndRankMomentum(AssetDataType assetDataType, LocalDate asOfDate,
                                                                 Integer entryRank, Integer retentionRank, String allocationMode) {
        return calculateAndRankMomentum(assetDataType, asOfDate, entryRank, retentionRank, allocationMode,
                null, null, null, null, null, null, null, null);
    }

    @Transactional
    public RiskAdjustedMomentumResult calculateAndRankMomentum(AssetDataType assetDataType, LocalDate asOfDate,
                                                                 Integer entryRank, Integer retentionRank, String allocationMode,
                                                                 String benchmark, String stopModel, Double trailingStopPercent,
                                                                 Integer atrPeriod, Double atrMultiplier, Integer benchmarkSmaPeriod,
                                                                 Double breadthThresholdPercent, Double weakExposureCapPercent) {
        LocalDate calculationDate = asOfDate == null ? LocalDate.now(MARKET_TIME_ZONE) : asOfDate;
        int effectiveEntryRank = entryRank != null ? entryRank : RiskAdjustedMomentumConstants.ENTRY_RANK;
        int effectiveRetentionRank = retentionRank != null ? retentionRank : RiskAdjustedMomentumConstants.RETENTION_RANK;
        String effectiveAllocationMode = allocationMode != null ? allocationMode : RiskAdjustedMomentumConstants.DEFAULT_ALLOCATION_MODE;
        if (!"EQUAL_WEIGHT".equals(effectiveAllocationMode) && !"INVERSE_VOL".equals(effectiveAllocationMode)) {
            throw new IllegalArgumentException("allocationMode must be EQUAL_WEIGHT or INVERSE_VOL");
        }
        String effectiveBenchmark = benchmark != null ? benchmark : RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK;
        String effectiveStopModel = stopModel != null ? stopModel.trim().toUpperCase(Locale.ROOT) : RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL;
        if (!List.of("FIXED", "TIERED", "ATR").contains(effectiveStopModel)) {
            throw new IllegalArgumentException("Stop model must be FIXED, TIERED or ATR");
        }
        double effectiveTrailingStopPercent = trailingStopPercent != null ? trailingStopPercent : RiskAdjustedMomentumConstants.DEFAULT_TRAILING_STOP_PERCENT;
        int effectiveAtrPeriod = atrPeriod != null ? atrPeriod : RiskAdjustedMomentumConstants.DEFAULT_ATR_PERIOD;
        double effectiveAtrMultiplier = atrMultiplier != null ? atrMultiplier : RiskAdjustedMomentumConstants.DEFAULT_ATR_MULTIPLIER;
        int effectiveBenchmarkSmaPeriod = benchmarkSmaPeriod != null ? benchmarkSmaPeriod : RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK_SMA_PERIOD;
        double effectiveBreadthThresholdPercent = breadthThresholdPercent != null ? breadthThresholdPercent : RiskAdjustedMomentumConstants.DEFAULT_BREADTH_THRESHOLD_PERCENT;
        double effectiveWeakExposureCapPercent = weakExposureCapPercent != null ? weakExposureCapPercent : RiskAdjustedMomentumConstants.DEFAULT_WEAK_EXPOSURE_CAP_PERCENT;

        RiskAdjustedMomentumResult calculation = calculateMomentum(assetDataType, calculationDate);
        if (!calculation.isValid()) {
            return calculation;
        }

        Map<String, NavigableMap<LocalDate, OHLCV>> universeSeries =
                RiskAdjustedMomentumRiskOverlayUtil.normalize(universeRawData(assetDataType));
        Map<String, NavigableMap<LocalDate, OHLCV>> indexSeries =
                RiskAdjustedMomentumRiskOverlayUtil.normalize(stockPriceCacheService.getCachedAllIndexPriceData());
        NavigableMap<LocalDate, OHLCV> benchmarkSeries = RiskAdjustedMomentumRiskOverlayUtil.resolveBenchmark(indexSeries, effectiveBenchmark);
        double breadthPercent = RiskAdjustedMomentumRiskOverlayUtil.breadth(universeSeries, calculationDate, effectiveBenchmarkSmaPeriod);
        boolean benchmarkAboveSma = RiskAdjustedMomentumRiskOverlayUtil.aboveSma(benchmarkSeries, calculationDate, effectiveBenchmarkSmaPeriod);
        double exposureCapPercent = breadthPercent < effectiveBreadthThresholdPercent ? effectiveWeakExposureCapPercent : 100;
        boolean newBuysAllowed = benchmarkAboveSma;
        int effectiveGatedEntryRank = newBuysAllowed ? Math.max(0, (int) Math.floor(effectiveEntryRank * exposureCapPercent / 100.0)) : 0;

        assignRanks(assetDataType, calculationDate, effectiveGatedEntryRank, effectiveAllocationMode);
        List<TopN_RiskAdjustedMomentumAssetType> ranked = topRiskAdjustedMomentumStockRepository
                .findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(
                        assetDataType, java.sql.Date.valueOf(calculationDate)).stream()
                .sorted(Comparator.comparingInt(TopN_RiskAdjustedMomentumAssetType::getTotalRankScore)
                        .thenComparing(TopN_RiskAdjustedMomentumAssetType::getStockName))
                .toList();
        List<String> topStockNames = ranked.stream()
                .limit(effectiveGatedEntryRank)
                .map(TopN_RiskAdjustedMomentumAssetType::getStockName)
                .toList();
        List<String> retainedStockNames = ranked.stream()
                .limit(Math.max(0, effectiveRetentionRank))
                .map(TopN_RiskAdjustedMomentumAssetType::getStockName)
                .toList();

        Map<String, RiskAdjustedMomentum> byName = calculation.getAllStocks().stream()
                .collect(Collectors.toMap(RiskAdjustedMomentum::getStockName, item -> item, (a, b) -> a));
        for (TopN_RiskAdjustedMomentumAssetType entity : ranked) {
            RiskAdjustedMomentum stock = byName.get(entity.getStockName());
            if (stock == null) continue;
            stock.setRank12Months(entity.getRank12Months());
            stock.setRank6Months(entity.getRank6Months());
            stock.setRank3Months(entity.getRank3Months());
            stock.setTotalRankScore(entity.getTotalRankScore());
        }
        for (String stockName : topStockNames) {
            RiskAdjustedMomentum stock = byName.get(stockName);
            NavigableMap<LocalDate, OHLCV> series = universeSeries.get(stockName);
            if (stock == null || series == null) continue;
            OHLCV bar = RiskAdjustedMomentumRiskOverlayUtil.barAtOrBefore(series, calculationDate);
            if (bar == null) continue;
            double price = bar.getClose();
            double level = RiskAdjustedMomentumRiskOverlayUtil.stopLevel(effectiveStopModel, price, price, series, calculationDate,
                    effectiveTrailingStopPercent, effectiveAtrPeriod, effectiveAtrMultiplier);
            stock.setStopLossLevel((float) level);
        }

        return new RiskAdjustedMomentumResult(
                calculation.getAllStocks(),
                calculation.getQualifiedStocks(),
                topStockNames,
                retainedStockNames,
                true,
                "Risk-adjusted momentum calculation and ranking completed successfully",
                new RiskAdjustedMomentumRegimeOverlay(effectiveBenchmark, breadthPercent, benchmarkAboveSma,
                        exposureCapPercent, newBuysAllowed, effectiveGatedEntryRank)
        );
    }

    public RiskAdjustedMomentumResult calculateMomentum(AssetDataType assetDataType, LocalDate asOfDate) {
        try {
            if (asOfDate.isAfter(LocalDate.now(MARKET_TIME_ZONE))) {
                throw new IllegalArgumentException("As-of date cannot be in the future");
            }
            log.info("Calculating {} risk-adjusted momentum as of {}", assetDataType, asOfDate);
            Map<String, List<OHLCV>> stockData = universeRawData(assetDataType);
            validateInput(stockData);

            List<RiskAdjustedMomentum> allResults = new ArrayList<>();
            List<TopN_RiskAdjustedMomentumAssetType> topNList = new ArrayList<>();
            int count = 0;
            for (Map.Entry<String, List<OHLCV>> entry : stockData.entrySet()) {
                String stockName = entry.getKey();
                List<OHLCV> ohlcData = entry.getValue();
                count++;
                try {
                    RiskAdjustedMomentum momentum = calculateStockMomentum(stockName, ohlcData, asOfDate);
                    if (momentum != null) {
                        allResults.add(momentum);
                        if (momentum.isQualifiesForMomentum()) {
                            TopN_RiskAdjustedMomentumAssetType topNAsset = new TopN_RiskAdjustedMomentumAssetType();
                            topNAsset.setAssetDataType(assetDataType);
                            topNAsset.setStockName(momentum.getStockName());
                            topNAsset.setPercentageReturn12Months(momentum.getOneYearReturn());
                            topNAsset.setPercentageReturn6Months(momentum.getSixMonthReturn());
                            topNAsset.setPercentageReturn3Months(momentum.getThreeMonthReturn());
                            topNAsset.setVolatility(momentum.getVolatility());
                            topNAsset.setStrategyRunDate(java.sql.Date.valueOf(momentum.getStrategyRunDate()));
                            topNList.add(topNAsset);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error calculating risk-adjusted momentum for {}: {}", stockName, e.getMessage());
                }
                log.info("Calculation in progress remaining assets to process: {}", stockData.size() - count);
            }
            if (allResults.isEmpty()) {
                return new RiskAdjustedMomentumResult(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), false,
                        "No assets have complete 12-1 month, 6-month and 3-month price history on or before "
                                + asOfDate + ". Select a later date or load older price data.");
            }
            java.sql.Date strategyRunDate = java.sql.Date.valueOf(asOfDate);
            topRiskAdjustedMomentumStockRepository.deleteByAssetDataTypeAndStrategyRunDate(assetDataType, strategyRunDate);
            topRiskAdjustedMomentumStockRepository.flush();
            if (!topNList.isEmpty()) {
                topRiskAdjustedMomentumStockRepository.saveAll(topNList);
            }
            List<RiskAdjustedMomentum> sortedResults = allResults.stream()
                    .sorted(Comparator.comparingDouble(RiskAdjustedMomentum::getOneYearReturn).reversed())
                    .collect(Collectors.toList());

            List<RiskAdjustedMomentum> qualifiedStocks = sortedResults.stream()
                    .filter(RiskAdjustedMomentum::isQualifiesForMomentum)
                    .collect(Collectors.toList());

            List<String> topStockNames = qualifiedStocks.stream()
                    .limit(RiskAdjustedMomentumConstants.ENTRY_RANK)
                    .map(RiskAdjustedMomentum::getStockName)
                    .collect(Collectors.toList());

            return new RiskAdjustedMomentumResult(sortedResults, qualifiedStocks, topStockNames, topStockNames,
                    true, "Risk-adjusted momentum calculation successful");

        } catch (Exception e) {
            return new RiskAdjustedMomentumResult(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), false, e.getMessage());
        }
    }

    RiskAdjustedMomentum calculateStockMomentum(String stockName, List<OHLCV> ohlcData, LocalDate asOfDate) {
        if (ohlcData == null || ohlcData.isEmpty()) {
            return null;
        }
        NavigableMap<LocalDate, OHLCV> uniqueBars = new TreeMap<>();
        ohlcData.stream().filter(Objects::nonNull)
                .filter(bar -> bar.getDate() != null)
                .filter(bar -> !DateUtil.convertDateToLocalDate(bar.getDate()).isAfter(asOfDate))
                .forEach(bar -> uniqueBars.put(DateUtil.convertDateToLocalDate(bar.getDate()), bar));
        List<OHLCV> bars = new ArrayList<>(uniqueBars.values());
        if (bars.size() < RiskAdjustedMomentumConstants.MIN_DATA_POINTS) {
            log.error("Insufficient data points for {}. Required: {}, Provided: {}",
                    stockName, RiskAdjustedMomentumConstants.MIN_DATA_POINTS, bars.size());
            return null;
        }
        int currentIndex = bars.size() - 1;
        int skip = RiskAdjustedMomentumConstants.SKIP_MONTH_BARS;
        double skippedPrice = bars.get(currentIndex - skip).getClose();
        double previous1YearPrice = bars.get(currentIndex - 252 - skip).getClose();
        double currentPrice = bars.get(currentIndex).getClose();
        double previous6MonthPrice = bars.get(currentIndex - 126).getClose();
        double previous3MonthPrice = bars.get(currentIndex - 63).getClose();
        if (currentPrice <= 0 || skippedPrice <= 0 || previous1YearPrice <= 0 || previous6MonthPrice <= 0 || previous3MonthPrice <= 0) return null;

        Float oneYearReturn = percentageReturn(skippedPrice, previous1YearPrice);
        Float sixMonthReturn = percentageReturn(currentPrice, previous6MonthPrice);
        Float threeMonthReturn = percentageReturn(currentPrice, previous3MonthPrice);
        Float volatility = trailingVolatility(bars, currentIndex);
        log.info("12-1 momentum {} as of {}: 12-1={}%, 6M={}%, 3M={}%, vol={}%",
                stockName, uniqueBars.lastKey(), oneYearReturn, sixMonthReturn, threeMonthReturn, volatility);
        return new RiskAdjustedMomentum(stockName, oneYearReturn, sixMonthReturn, threeMonthReturn, volatility, asOfDate);
    }

    /** Annualized standard deviation of daily returns over the trailing VOLATILITY_LOOKBACK_BARS bars. */
    private Float trailingVolatility(List<OHLCV> bars, int currentIndex) {
        int window = RiskAdjustedMomentumConstants.VOLATILITY_LOOKBACK_BARS;
        int from = Math.max(1, currentIndex - window + 1);
        List<Double> dailyReturns = new ArrayList<>();
        for (int i = from; i <= currentIndex; i++) {
            double previous = bars.get(i - 1).getClose(), current = bars.get(i).getClose();
            if (previous > 0) dailyReturns.add(current / previous - 1);
        }
        if (dailyReturns.size() < 2) return 0f;
        double mean = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = dailyReturns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).sum() / (dailyReturns.size() - 1);
        return (float) (Math.sqrt(variance) * Math.sqrt(252) * 100.0);
    }

    private Float percentageReturn(double currentPrice, double previousPrice) {
        return (float) ((currentPrice / previousPrice - 1.0) * 100.0);
    }

    public void assignRanks(AssetDataType assetDataType, LocalDate asOfDate) {
        assignRanks(assetDataType, asOfDate, RiskAdjustedMomentumConstants.ENTRY_RANK, RiskAdjustedMomentumConstants.DEFAULT_ALLOCATION_MODE);
    }

    public void assignRanks(AssetDataType assetDataType, LocalDate asOfDate, int entryRank, String allocationMode) {
        List<TopN_RiskAdjustedMomentumAssetType> momentumAssetList =
                topRiskAdjustedMomentumStockRepository.findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(
                        assetDataType, java.sql.Date.valueOf(asOfDate));

        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_RiskAdjustedMomentumAssetType::getPercentageReturn12Months).reversed(),
                TopN_RiskAdjustedMomentumAssetType::setRank12Months);

        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_RiskAdjustedMomentumAssetType::getPercentageReturn6Months).reversed(),
                TopN_RiskAdjustedMomentumAssetType::setRank6Months);

        rankMomentumAsset(momentumAssetList,
                Comparator.comparing(TopN_RiskAdjustedMomentumAssetType::getPercentageReturn3Months).reversed(),
                TopN_RiskAdjustedMomentumAssetType::setRank3Months);

        momentumAssetList.forEach(stock -> {
            int totalRank = stock.getRank12Months() * RiskAdjustedMomentumConstants.WEIGHT_12_MONTHS
                    + stock.getRank6Months() * RiskAdjustedMomentumConstants.WEIGHT_6_MONTHS
                    + stock.getRank3Months() * RiskAdjustedMomentumConstants.WEIGHT_3_MONTHS;
            stock.setTotalRankScore(totalRank);
        });

        // Position weights for the top-entryRank cohort only, normalized to sum to 1; zero elsewhere.
        List<TopN_RiskAdjustedMomentumAssetType> entryCohort = momentumAssetList.stream()
                .sorted(Comparator.comparingInt(TopN_RiskAdjustedMomentumAssetType::getTotalRankScore)
                        .thenComparing(TopN_RiskAdjustedMomentumAssetType::getStockName))
                .limit(entryRank)
                .toList();
        Set<TopN_RiskAdjustedMomentumAssetType> entryCohortSet = new HashSet<>(entryCohort);
        if ("INVERSE_VOL".equals(allocationMode)) {
            double inverseVolSum = entryCohort.stream()
                    .mapToDouble(stock -> stock.getVolatility() > 0 ? 1.0 / stock.getVolatility() : 0.0).sum();
            momentumAssetList.forEach(stock -> {
                if (!entryCohortSet.contains(stock)) { stock.setInverseVolWeight(0f); return; }
                double inverseVol = stock.getVolatility() > 0 ? 1.0 / stock.getVolatility() : 0.0;
                stock.setInverseVolWeight(inverseVolSum == 0 ? 0f : (float) (inverseVol / inverseVolSum));
            });
        } else {
            float equalWeight = entryCohort.isEmpty() ? 0f : 1f / entryCohort.size();
            momentumAssetList.forEach(stock -> stock.setInverseVolWeight(entryCohortSet.contains(stock) ? equalWeight : 0f));
        }

        topRiskAdjustedMomentumStockRepository.saveAll(momentumAssetList);
        log.info("Risk-adjusted momentum rankings updated successfully (12-1 signal, inverse-vol weighted).");
    }

    private void rankMomentumAsset(List<TopN_RiskAdjustedMomentumAssetType> momentumAssetList,
                                    Comparator<TopN_RiskAdjustedMomentumAssetType> comparator,
                                    BiConsumer<TopN_RiskAdjustedMomentumAssetType, Integer> rankSetter) {
        List<TopN_RiskAdjustedMomentumAssetType> sorted = momentumAssetList.stream()
                .sorted(comparator)
                .toList();

        IntStream.range(0, sorted.size())
                .forEach(i -> rankSetter.accept(sorted.get(i), i + 1));
    }

    private void validateInput(Map<String, List<OHLCV>> stockData) {
        if (stockData == null || stockData.isEmpty()) {
            throw new IllegalArgumentException("Stock data cannot be null or empty");
        }
        for (Map.Entry<String, List<OHLCV>> entry : stockData.entrySet()) {
            String stockName = entry.getKey();
            if (stockName == null || stockName.trim().isEmpty()) {
                throw new IllegalArgumentException("Stock name cannot be null or empty");
            }
        }
    }
}
