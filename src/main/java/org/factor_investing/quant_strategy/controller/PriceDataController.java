package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.service.PriceDataService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
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
}
