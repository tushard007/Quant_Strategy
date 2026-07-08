package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.ETFPricesJson;
import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class StockPriceCacheService {


    private final StockDataService stockDataService;

    public StockPriceCacheService(StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    public Map<String, List<OHLCV>> getAllStockPriceData() {
        List<StockPricesJson> stockPricesJsonList = stockDataService.getAllStockData();
        Map<String, List<OHLCV>> stockPriceDataMap = stockPricesJsonList.stream().
                filter(stockPricesJson -> stockPricesJson.getNseStockMasterData() != null
                        && stockPricesJson.getNseStockMasterData().getSymbol() != null)
                .collect(Collectors.toMap(
                        stockPrice -> stockPrice.getNseStockMasterData().getSymbol(),
                        StockPricesJson::getOhlcvData
                ));
        log.info("Retrieved all stock price data with {} entries.", stockPriceDataMap.size() +" on Date & time: "+ DateUtil.getCurrentDateTime());
        return stockPriceDataMap;

    }
    @Scheduled(cron = "0 0 19 * * MON-FRI", zone = "Asia/Kolkata")
    public Map<String, List<OHLCV>> getAllETFPriceData() {
        List<ETFPricesJson> etfPricesJsonList = stockDataService.getAllETFData();
        Map<String, List<OHLCV>> stockPriceDataMap = etfPricesJsonList.stream().
                filter(etfPricesJson -> etfPricesJson.getNseETFMasterData() != null
                        && etfPricesJson.getNseETFMasterData().getSymbol() != null)
                .collect(Collectors.toMap(
                        etfPrice -> etfPrice.getNseETFMasterData().getSymbol(),
                        ETFPricesJson::getOhlcvData
                ));
        log.info("Retrieved all ETF price data with {} entries.", stockPriceDataMap.size()+" on Date & time: "+ DateUtil.getCurrentDateTime());
        return stockPriceDataMap;

    }

    @Scheduled(cron = "0 30 19 * * MON-FRI", zone = "Asia/Kolkata")
    public Map<String, List<OHLCV>> getAllIndexPriceData() {
        List<IndexPricesJson> indexPricesJsonList = stockDataService.getAllIndexData();
        Map<String, List<OHLCV>> indexPriceDataMap = indexPricesJsonList.stream().
                filter(indexPricesJson -> indexPricesJson.getNseIndexMasterData() != null
                        && indexPricesJson.getNseIndexMasterData().getSymbol() != null)
                .collect(Collectors.toMap(
                        indexPrice -> indexPrice.getNseIndexMasterData().getSymbol(),
                        IndexPricesJson::getOhlcvData
                ));
        log.info("Retrieved all index price data with {} entries.", indexPriceDataMap.size()+" on Date & time: "+ DateUtil.getCurrentDateTime());
        return indexPriceDataMap;

    }

    /**
     * Retrieves the closing price of a stock by its symbol and date.
     *
     * @param symbol The stock symbol.
     * @param date   The date for which to retrieve the closing price.
     * @return The closing price of the stock on the specified date, or null if not found.
     */
    public Double getStockClosingPriceBySymbolAndDate(String symbol, LocalDate date,AssetDataType assetDataType) {

        Map<String, List<OHLCV>> allStockPriceData = null;
        if(AssetDataType.STOCK==assetDataType) {
            allStockPriceData = getCachedAllStockPriceData();
        }
        if(AssetDataType.ETF ==assetDataType) {
            allStockPriceData = getCachedAllETFPriceData();
        }
        if(AssetDataType.INDEX ==assetDataType) {
            allStockPriceData = getCachedAllIndexPriceData();
        }
        List<OHLCV> ohlcvList = allStockPriceData.get(symbol);
        Double stockClosingPrice = 0.0;

        if (ohlcvList != null) {
            for (OHLCV ohlcv : ohlcvList) {
                LocalDate ohlcvDate = DateUtil.convertDateToLocalDate(ohlcv.getDate());
                if (ohlcvDate.isEqual(date)) {
                    stockClosingPrice = ohlcv.getClose();
                    break;
                }
            }
        }
        return stockClosingPrice;
    }

    /**
     * Retrieves all stock price dates for a given stock symbol.
     *
     * @param symbol The stock symbol.
     * @return A set of dates for which stock prices are available for the specified symbol.
     */
    public Set<java.util.Date> getAllAssetWisePriceDateBySymbol(String symbol, AssetDataType assetDataType) {
        Map<String, List<OHLCV>> allStockPriceData=null;
        if(AssetDataType.STOCK==assetDataType) {
            allStockPriceData= getCachedAllStockPriceData();
       }
        if(AssetDataType.ETF ==assetDataType) {
            allStockPriceData = getCachedAllETFPriceData();
        }
        if(AssetDataType.INDEX ==assetDataType) {
            allStockPriceData = getCachedAllIndexPriceData();
        }
        List<OHLCV> ohlcvList = allStockPriceData.get(symbol);
        return ohlcvList.stream()
                .map(OHLCV::getDate)
                .collect(Collectors.toSet());

    }
    // --- Cache Implementation ---

    // Use ConcurrentHashMap for thread safety in a multi threaded environment like Spring.
    private final Map<String, List<OHLCV>> stockDataCache = new ConcurrentHashMap<>();
    private final Map<String, List<OHLCV>> etfDataCache = new ConcurrentHashMap<>();
    private final Map<String, List<OHLCV>> indexDataCache = new ConcurrentHashMap<>();


    // Tracks when the cache was last successfully populated.
    private long lastStockCacheTime = 0L;
    private long lastETFCacheTime = 0L;
    private long lastIndexCacheTime = 0L;

    // Set cache validity for 5 minutes.
    private static final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(60);


    /**
     * Retrieves all stock price data, utilizing a time-based local cache.
     *
     * @return A map of stock names to their OHLCV data.
     */
    @EventListener(ApplicationStartedEvent.class)
    public Map<String, List<OHLCV>> getCachedAllStockPriceData() {
        long currentTime = System.currentTimeMillis();

        // Check if the cache is populated and if it's still valid.
        if (!stockDataCache.isEmpty() && (currentTime - lastStockCacheTime < CACHE_DURATION_MS)) {
            log.info("Returning data from cache."); // For logging/debugging
            return stockDataCache;
        }

        return refreshStockPriceDataCache();
    }

    @EventListener(ApplicationStartedEvent.class)
    public Map<String, List<OHLCV>> getCachedAllETFPriceData() {
        long currentTime = System.currentTimeMillis();

        if (!etfDataCache.isEmpty() && (currentTime - lastETFCacheTime < CACHE_DURATION_MS)) {
            log.info("Returning ETF data from cache.");
            return etfDataCache;
        }

        return refreshETFPriceDataCache();
    }

    @EventListener(ApplicationStartedEvent.class)
    public Map<String, List<OHLCV>> getCachedAllIndexPriceData() {
        long currentTime = System.currentTimeMillis();

        // Check if the cache is populated and if it's still valid.
        if (!indexDataCache.isEmpty() && (currentTime - lastIndexCacheTime < CACHE_DURATION_MS)) {
            log.info("Returning index data from cache."); // For logging/debugging
            return indexDataCache;
        }

        return refreshIndexPriceDataCache();
    }

    public Map<String, List<OHLCV>> refreshStockPriceDataCache() {
        Map<String, List<OHLCV>> freshData = getAllStockPriceData();

        this.stockDataCache.clear();
        this.stockDataCache.putAll(freshData);
        this.lastStockCacheTime = System.currentTimeMillis();
        log.info(" fresh cache size: {}", this.stockDataCache.size());
        return this.stockDataCache;
    }

    public Map<String, List<OHLCV>> refreshIndexPriceDataCache() {
        Map<String, List<OHLCV>> freshIndexData = getAllIndexPriceData();

        this.indexDataCache.clear();
        this.indexDataCache.putAll(freshIndexData);
        this.lastIndexCacheTime = System.currentTimeMillis();
        log.info(" fresh Index cache size: {}", this.indexDataCache.size());
        return this.indexDataCache;
    }

    public Map<String, List<OHLCV>> refreshETFPriceDataCache() {
        Map<String, List<OHLCV>> freshETFData = getAllETFPriceData();

        this.etfDataCache.clear();
        this.etfDataCache.putAll(freshETFData);
        this.lastETFCacheTime = System.currentTimeMillis();
        log.info(" fresh ETF cache size: {}", this.etfDataCache.size());
        return this.etfDataCache;
    }

}
