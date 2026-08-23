package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumResult;
import org.factor_investing.quant_strategy.strategies.momentum.StockMomentumService;
import org.factor_investing.quant_strategy.model.response.MomentumExecutionSummary;
import org.factor_investing.quant_strategy.model.response.SavedMomentumResult;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/momentum")
public class MomentumController {

    private final StockMomentumService momentumService;

    public MomentumController(StockMomentumService momentumService) {
        this.momentumService = momentumService;
    }

    /**
     * Calculates momentum data and, only when calculation succeeds,
     * assigns rankings for the requested asset type.
     */
    @PostMapping("/calculate-and-rank/{assetDataType}")
    public ResponseEntity<MomentumResult> calculateAndRank(
            @PathVariable AssetDataType assetDataType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        MomentumResult result = momentumService.calculateAndRankMomentum(assetDataType, asOfDate);
        return result.isValid()
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/executions")
    public List<MomentumExecutionSummary> executionHistory(
            @RequestParam(required = false) AssetDataType assetDataType
    ) {
        return momentumService.getExecutionHistory(assetDataType);
    }

    @GetMapping("/executions/{assetDataType}/{strategyRunDate}")
    public List<SavedMomentumResult> savedResults(
            @PathVariable AssetDataType assetDataType,
            @PathVariable Date strategyRunDate
    ) {
        return momentumService.getSavedResults(assetDataType, strategyRunDate);
    }
}
