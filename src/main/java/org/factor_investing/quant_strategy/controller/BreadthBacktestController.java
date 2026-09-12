package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.BreadthBacktestRunType;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthFilteredMomentumBacktestRequest;
import org.factor_investing.quant_strategy.model.request.BreadthThresholdSensitivityRequest;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunDetail;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunSummary;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.BreadthThresholdSensitivityResult;
import org.factor_investing.quant_strategy.model.response.ForwardReturnAnalysisResult;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthBacktestHistoryService;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthFilteredMomentumBacktestService;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthThresholdSensitivityService;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.ForwardReturnAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/breadth-backtest")
public class BreadthBacktestController {
    private final BreadthFilteredMomentumBacktestService backtestService;
    private final ForwardReturnAnalysisService forwardReturnAnalysisService;
    private final BreadthThresholdSensitivityService sensitivityService;
    private final BreadthBacktestHistoryService historyService;

    public BreadthBacktestController(BreadthFilteredMomentumBacktestService backtestService,
                                      ForwardReturnAnalysisService forwardReturnAnalysisService,
                                      BreadthThresholdSensitivityService sensitivityService,
                                      BreadthBacktestHistoryService historyService) {
        this.backtestService = backtestService;
        this.forwardReturnAnalysisService = forwardReturnAnalysisService;
        this.sensitivityService = sensitivityService;
        this.historyService = historyService;
    }

    @PostMapping("/run")
    public ResponseEntity<BreadthFilteredMomentumBacktestResult> run(@RequestBody BreadthFilteredMomentumBacktestRequest request) {
        BreadthFilteredMomentumBacktestResult result = backtestService.run(request);
        long qualifyingSampleCount = result.breadthFiltered().regimeExposureHistory().stream()
                .filter(org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult.RegimePoint::newBuysAllowed)
                .count();
        UUID runId = historyService.saveBacktest(request, result, qualifyingSampleCount);
        return ResponseEntity.ok().header("X-Backtest-Run-Id", runId.toString()).body(result);
    }

    @PostMapping("/forward-return-analysis")
    public ForwardReturnAnalysisResult forwardReturnAnalysis(
            @RequestParam NiftyIndexName universe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "NIFTY 500") String benchmark) {
        return forwardReturnAnalysisService.analyze(universe, from, to, benchmark);
    }

    @PostMapping("/threshold-sensitivity")
    public ResponseEntity<BreadthThresholdSensitivityResult> thresholdSensitivity(@RequestBody BreadthThresholdSensitivityRequest request) {
        BreadthThresholdSensitivityResult result = sensitivityService.run(request);
        UUID runId = historyService.saveSensitivity(request, result);
        return ResponseEntity.ok().header("X-Backtest-Run-Id", runId.toString()).body(result);
    }

    @GetMapping("/executions")
    public List<BreadthBacktestRunSummary> executions(@RequestParam(required = false) BreadthBacktestRunType runType) {
        return historyService.history(runType);
    }

    @GetMapping("/executions/{runId}")
    public BreadthBacktestRunDetail execution(@PathVariable UUID runId) {
        return historyService.toDetail(historyService.getEntity(runId));
    }
}
