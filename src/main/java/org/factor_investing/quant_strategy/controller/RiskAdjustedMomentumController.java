package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.RiskAdjustedMomentumResult;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.RiskAdjustedMomentumService;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumExecutionSummary;
import org.factor_investing.quant_strategy.model.response.SavedRiskAdjustedMomentumResult;
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
@RequestMapping("/api/risk-adjusted-momentum")
public class RiskAdjustedMomentumController {

    private final RiskAdjustedMomentumService riskAdjustedMomentumService;

    public RiskAdjustedMomentumController(RiskAdjustedMomentumService riskAdjustedMomentumService) {
        this.riskAdjustedMomentumService = riskAdjustedMomentumService;
    }

    @PostMapping("/calculate-and-rank/{assetDataType}")
    public ResponseEntity<RiskAdjustedMomentumResult> calculateAndRank(
            @PathVariable AssetDataType assetDataType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Integer entryRank,
            @RequestParam(required = false) Integer retentionRank,
            @RequestParam(required = false) String allocationMode,
            @RequestParam(required = false) String benchmark,
            @RequestParam(required = false) String stopModel,
            @RequestParam(required = false) Double trailingStopPercent,
            @RequestParam(required = false) Integer atrPeriod,
            @RequestParam(required = false) Double atrMultiplier,
            @RequestParam(required = false) Integer benchmarkSmaPeriod,
            @RequestParam(required = false) Double breadthThresholdPercent,
            @RequestParam(required = false) Double weakExposureCapPercent
    ) {
        RiskAdjustedMomentumResult result = riskAdjustedMomentumService.calculateAndRankMomentum(
                assetDataType, asOfDate, entryRank, retentionRank, allocationMode,
                benchmark, stopModel, trailingStopPercent, atrPeriod, atrMultiplier,
                benchmarkSmaPeriod, breadthThresholdPercent, weakExposureCapPercent);
        return result.isValid()
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/executions")
    public List<RiskAdjustedMomentumExecutionSummary> executionHistory(
            @RequestParam(required = false) AssetDataType assetDataType
    ) {
        return riskAdjustedMomentumService.getExecutionHistory(assetDataType);
    }

    @GetMapping("/executions/{assetDataType}/{strategyRunDate}")
    public List<SavedRiskAdjustedMomentumResult> savedResults(
            @PathVariable AssetDataType assetDataType,
            @PathVariable Date strategyRunDate
    ) {
        return riskAdjustedMomentumService.getSavedResults(assetDataType, strategyRunDate);
    }
}
