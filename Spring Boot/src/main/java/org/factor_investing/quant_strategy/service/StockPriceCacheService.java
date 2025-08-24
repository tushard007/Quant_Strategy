package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.StockPriceDataMapper;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockPriceCacheService {


    @Autowired
    private StockDataService stockDataService;

    @EventListener(ApplicationStartedEvent.class)
    @Cacheable(value = "stockPrices", key = "#symbol")
    public Map<String, List<OHLCV>> getAllStockPriceData() {
        List<StockPricesJson> stockPricesJsonList = stockDataService.getAllStockData();
        Map<String, List<OHLCV>> stockPriceDataMap = stockPricesJsonList.stream().limit(10)
                .collect(Collectors.toMap(
                        stockPrice -> stockPrice.getNseStockMasterData().getSymbol(),
                        StockPricesJson::getOhlcvData
                ));
        log.info("Retrieved all stock price data with {} entries.", stockPriceDataMap.size());
        return stockPriceDataMap;

    }

    public void getAllStockPriceDataMapper() {
        Map<String, List<OHLCV>> allStockPriceData = getAllStockPriceData();
        // Convert to StockPriceDataMapper for further processing if neededst
        AtomicReference<StockPriceDataMapper> stockPriceDataMapper = null;
        allStockPriceData.forEach((key, value) -> {
            String symbol = key;
            value.forEach(ohlcv -> {
                double close = ohlcv.getOpen();
                Date date = (Date) ohlcv.getDate();
                stockPriceDataMapper.set(new StockPriceDataMapper(symbol, date, close));
            });
        });

    }

    /**
     * Retrieves the closing price of a stock by its symbol and date.
     *
     * @param symbol The stock symbol.
     * @param date   The date for which to retrieve the closing price.
     * @return The closing price of the stock on the specified date, or null if not found.
     */
    public Double getStockClosingPriceBySymbolAndDate(String symbol, LocalDate date) {
        Map<String, List<OHLCV>> allStockPriceData = getAllStockPriceData();
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
        Map<String, List<OHLCV>> allStockPriceData = getAllStockPriceData();
        List<OHLCV> ohlcvList = allStockPriceData.get(symbol);
        return ohlcvList.stream()
                .map(OHLCV::getDate)
                .collect(Collectors.toSet());

    }
}

