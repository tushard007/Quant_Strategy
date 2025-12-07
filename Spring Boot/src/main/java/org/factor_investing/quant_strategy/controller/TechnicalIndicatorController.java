package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.technical_analysis.TechnicalIndicatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/technical-indicator")
public class TechnicalIndicatorController {
    @Autowired
    private TechnicalIndicatorService technicalIndicatorService;

    @GetMapping("EMAIndicator/{days}")
    public void calculateEMAIndicator(@PathVariable(required = true) int days) {
        technicalIndicatorService.emaIndicator(days);
    }



}