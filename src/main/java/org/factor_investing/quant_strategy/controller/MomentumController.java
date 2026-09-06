package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
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
    private final NiftyIndexStockService niftyIndexStockService;

    public MomentumController(StockMomentumService momentumService, NiftyIndexStockService niftyIndexStockService) {
        this.momentumService = momentumService;
        this.niftyIndexStockService = niftyIndexStockService;
    }

    /**
     * Calculates momentum data and, only when calculation succeeds,
     * assigns rankings for the requested asset type.
     */
    @PostMapping("/calculate-and-rank/{assetDataType}")
    public ResponseEntity<MomentumResult> calculateAndRank(
            @PathVariable AssetDataType assetDataType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) NiftyIndexName niftyIndex
    ) {
        List<String> symbols = assetDataType == AssetDataType.STOCK && niftyIndex != null
                ? niftyIndexStockService.symbolsForIndex(niftyIndex)
                : null;
        MomentumResult result = momentumService.calculateAndRankMomentum(assetDataType, asOfDate, symbols, niftyIndex);
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
