package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.service.PriceDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@Slf4j
@RequestMapping("/api/price-data")
public class PriceDataController {
    private final PriceDataService priceDataService;
    public PriceDataController(PriceDataService priceDataService) {
        this.priceDataService = priceDataService;
    }

    @PostMapping("/stock-Price/{timeFrame}")
    public String saveOrUpdateStockPrice(@PathVariable PriceFrequencey timeFrame) throws ParseException {
       return priceDataService.saveOrUpdateStockPriceData(timeFrame);
    }
    @PostMapping("/ETF-Price/{timeFrame}")
    public String saveOrUpdateETFPriceData(@PathVariable PriceFrequencey timeFrame) throws ParseException {
        return priceDataService.saveOrUpdateETFPriceData(timeFrame);
    }

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Kolkata")
    public void updateETFPriceData() throws ParseException {
        log.info("Starting scheduled at 4 PM IST (Weekdays only) for etf price update");
        priceDataService.saveOrUpdateETFPriceData(PriceFrequencey.DAILY);
    }
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Kolkata")
    public void updateStockPriceData() throws ParseException {
        log.info("Starting scheduled at 5 PM IST (Weekdays only) for stock price update");
        priceDataService.saveOrUpdateStockPriceData(PriceFrequencey.DAILY);
    }
}
