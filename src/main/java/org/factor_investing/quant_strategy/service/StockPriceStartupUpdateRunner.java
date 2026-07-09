package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(
        name = "stock-price.startup-update.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StockPriceStartupUpdateRunner implements ApplicationRunner {

    private final PriceDataService priceDataService;
    private final StockPriceCacheService stockPriceCacheService;

    public StockPriceStartupUpdateRunner(
            PriceDataService priceDataService,
            StockPriceCacheService stockPriceCacheService
    ) {
        this.priceDataService = priceDataService;
        this.stockPriceCacheService = stockPriceCacheService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            updateStockPricesOnStartup();
            updateETFPricesOnStartup();
            updateIndexPricesOnStartup();
        } catch (Exception e) {
            log.error("Price startup update failed", e);
        }
    }

    private void updateStockPricesOnStartup() throws Exception {

        if (priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.STOCK)) {

            log.info("Stock price data is already updated till current trading date. Skipping startup stock update.");

            stockPriceCacheService.refreshStockPriceDataCache();

            return;
        }

        log.info("Starting stock price update from last available price date on application startup");

        String result = priceDataService.updateStockPriceDataFromLastDate();

        log.info(result);

        stockPriceCacheService.refreshStockPriceDataCache();
    }

    private void updateETFPricesOnStartup() throws Exception {

        if (priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.ETF)) {

            log.info("ETF price data is already updated till current trading date. Skipping startup ETF update.");

            stockPriceCacheService.refreshETFPriceDataCache();

            return;
        }

        log.info("Starting ETF price update from last available price date on application startup");

        String result = priceDataService.updateETFPriceDataFromLastDate();

        log.info(result);

        stockPriceCacheService.refreshETFPriceDataCache();
    }

    private void updateIndexPricesOnStartup() throws Exception {

        if (priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.INDEX)) {

            log.info("Index price data is already updated till current trading date. Skipping startup index update.");

            stockPriceCacheService.refreshIndexPriceDataCache();

            return;
        }

        log.info("Starting index price update on application startup");

        String result = priceDataService.saveOrUpdateIndexPriceData(PriceFrequencey.DAILY);

        log.info(result);

        stockPriceCacheService.refreshIndexPriceDataCache();
    }
}
