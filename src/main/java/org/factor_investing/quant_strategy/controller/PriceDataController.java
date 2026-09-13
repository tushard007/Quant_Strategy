package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.service.PriceDataService;
import org.factor_investing.quant_strategy.service.PriceUpdateJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@RequestMapping("/api/price-data")
public class PriceDataController {
    private final PriceUpdateJobService jobs;
    private final PriceDataService priceDataService;
    public PriceDataController(PriceDataService priceDataService, PriceUpdateJobService jobs) {
        this.priceDataService = priceDataService;
        this.jobs = jobs;
    }

    @PostMapping("/jobs/{source}/{timeFrame}")
    public ResponseEntity<PriceUpdateJobService.Job> startJob(
            @PathVariable String source, @PathVariable PriceFrequencey timeFrame) {
        return ResponseEntity.accepted().body(jobs.start(source, timeFrame));
    }

    @GetMapping("/jobs/{id}")
    public PriceUpdateJobService.Job getJob(@PathVariable String id) {
        return jobs.get(id);
    }

    @PostMapping("/stock-Price/{timeFrame}")
    public String saveOrUpdateStockPrice(@PathVariable PriceFrequencey timeFrame) throws ParseException {
       return priceDataService.saveOrUpdateStockPriceData(timeFrame);
    }
    @PostMapping("/ETF-Price/{timeFrame}")
    public String saveOrUpdateETFPriceData(@PathVariable PriceFrequencey timeFrame) throws ParseException {
        return priceDataService.saveOrUpdateETFPriceData(timeFrame);
    }

    @PostMapping("/index-Price/{timeFrame}")
    public String saveOrUpdateIndexPriceData(@PathVariable PriceFrequencey timeFrame) throws ParseException {
        return priceDataService.saveOrUpdateIndexPriceData(timeFrame);
    }
}
