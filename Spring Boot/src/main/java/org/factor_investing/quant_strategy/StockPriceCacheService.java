package org.factor_investing.quant_strategy;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.StockPriceData;
import org.factor_investing.quant_strategy.repository.StockPriceDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StockPriceCacheService {

    // In-memory cache using ConcurrentHashMap for thread-safety
    private final Map<String, Float> stockPriceCache = new ConcurrentHashMap<>();

    @Autowired
    private StockPriceDataRepository stockPriceRepository;

    // Preload all stock prices into cache on application startup
    @EventListener(ApplicationStartedEvent.class)
    public void preloadStockPrices() {
        log.info("Preloading stock prices into cache...");

        List<StockPriceData> allStockPrices = stockPriceRepository.findAll();

        allStockPrices.forEach(stockPrice -> {
            String cacheKey = generateCacheKey(
                    stockPrice.getSymbol(),
                  stockPrice.getDate()
            );
            stockPriceCache.put(cacheKey, (float) stockPrice.getOpen());
        });

        log.info("Preloaded {} stock prices into cache", stockPriceCache.size());
    }

    // Generate a unique cache key
    private String generateCacheKey(String stockTicker, Date priceDate) {
        return stockTicker + "_" + priceDate.getTime();
    }

    // Retrieve stock price from cache
    public Float getStockPrice(String stockTicker, Date priceDate) {
        String cacheKey = generateCacheKey(stockTicker, priceDate);
        Float price = stockPriceCache.get(cacheKey);

        if (price == null) {
            log.error("Stock price not found for {} on {}", stockTicker, priceDate);
            return 0.0f;
        }
        return price;
    }

    // Bulk insert and update cache
    public void saveAllStockPrices(List<StockPriceData> stockPrices) {
        List<StockPriceData> savedPrices = stockPriceRepository.saveAll(stockPrices);

        savedPrices.forEach(stockPrice -> {
            String cacheKey = generateCacheKey(
                    stockPrice.getSymbol(),
                    stockPrice.getDate()
            );
            stockPriceCache.put(cacheKey, (float) stockPrice.getClose());
        });
    }

    // Method to refresh entire cache
    public void refreshCache() {
        stockPriceCache.clear();
        preloadStockPrices();
    }

    // Get cache size for monitoring
    public int getCacheSize() {
        return stockPriceCache.size();
    }
}

