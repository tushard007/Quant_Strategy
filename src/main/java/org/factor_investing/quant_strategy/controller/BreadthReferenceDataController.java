package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthReferenceDataReport;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthReferenceDataValidationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-breadth/reference-data")
public class BreadthReferenceDataController {

    private final BreadthReferenceDataValidationService breadthReferenceDataValidationService;

    public BreadthReferenceDataController(BreadthReferenceDataValidationService breadthReferenceDataValidationService) {
        this.breadthReferenceDataValidationService = breadthReferenceDataValidationService;
    }

    @GetMapping("/check")
    public BreadthReferenceDataReport check() {
        return breadthReferenceDataValidationService.checkRequiredIndices();
    }
}
