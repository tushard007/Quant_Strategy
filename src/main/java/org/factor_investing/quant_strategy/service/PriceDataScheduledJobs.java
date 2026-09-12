package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PriceDataScheduledJobs {
    private final PriceDataService priceDataService;

    public PriceDataScheduledJobs(PriceDataService priceDataService) {
        this.priceDataService = priceDataService;
    }

    @Scheduled(cron = "${price-data.etf.cron:0 0 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void updateEtfPrices() {
        try {
            log.info("Starting scheduled ETF price update");
            priceDataService.updateETFPriceDataFromLastDate();
        } catch (Exception exception) {
            log.error("Scheduled ETF price update failed", exception);
        }
    }

    @Scheduled(cron = "${price-data.stock.cron:0 0 17 * * MON-FRI}", zone = "Asia/Kolkata")
    public void updateStockPrices() {
        try {
            log.info("Starting scheduled stock price update");
            priceDataService.updateStockPriceDataFromLastDate();
        } catch (Exception exception) {
            log.error("Scheduled stock price update failed", exception);
        }
    }
}
