package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockPriceCacheService {


    @Autowired
    private StockDataService stockDataService;


    public Map<String, List<OHLCV>> getAllStockPriceData() {
        List<StockPricesJson> stockPricesJsonList = stockDataService.getAllStockData();
        Map<String, List<OHLCV>> stockPriceDataMap = stockPricesJsonList.stream()
                .collect(Collectors.toMap(
                        stockPrice -> stockPrice.getNseStockMasterData().getSymbol(),
                        StockPricesJson::getOhlcvData
                ));
        log.info("Retrieved all stock price data with {} entries.", stockPriceDataMap.size());
        return stockPriceDataMap;

    }

    /**
     * Retrieves the closing price of a stock by its symbol and date.
     *
     * @param symbol The stock symbol.
     * @param date   The date for which to retrieve the closing price.
     * @return The closing price of the stock on the specified date, or null if not found.
     */
    public Double getStockClosingPriceBySymbolAndDate(String symbol, LocalDate date) {
        Map<String, List<OHLCV>> allStockPriceData = getCachedAllStockPriceData();
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
    public Set<java.util.Date> getAllStockPriceDateBySymbol(String symbol) {
        Map<String, List<OHLCV>> allStockPriceData = getCachedAllStockPriceData();
        List<OHLCV> ohlcvList = allStockPriceData.get(symbol);
        return ohlcvList.stream()
                .map(OHLCV::getDate)
                .collect(Collectors.toSet());

    }
    // --- Cache Implementation ---

    // Use ConcurrentHashMap for thread safety in a multi threaded environment like Spring.
    private final Map<String, List<OHLCV>> stockDataCache = new ConcurrentHashMap<>();

    // Tracks when the cache was last successfully populated.
    private long lastCacheTime = 0L;

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
        if (!stockDataCache.isEmpty() && (currentTime - lastCacheTime < CACHE_DURATION_MS)) {
            log.info("Returning data from cache."); // For logging/debugging
            return stockDataCache;
        }

        // --- If cache is invalid or empty, fetch the data ---
        log.warn("Cache is empty or expired. Fetching fresh data.");

        // This is where you call your actual data fetching logic.
        Map<String, List<OHLCV>> freshData = getAllStockPriceData();

        // --- Update the cache ---
        // We clear and then putAll to ensure the cache is completely fresh.
        this.stockDataCache.clear();
        this.stockDataCache.putAll(freshData);
        this.lastCacheTime = currentTime; // Update the timestamp
        log.info(" fresh cache size: {}", this.stockDataCache.size());
        return this.stockDataCache;
    }

}

