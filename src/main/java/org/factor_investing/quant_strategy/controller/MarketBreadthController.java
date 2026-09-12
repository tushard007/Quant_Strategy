package org.factor_investing.quant_strategy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthRecalculationRequest;
import org.factor_investing.quant_strategy.model.response.BreadthRecalculationResponse;
import org.factor_investing.quant_strategy.model.response.BreadthSnapshotResponse;
import org.factor_investing.quant_strategy.model.response.SectorBreadthResponse;
import org.factor_investing.quant_strategy.service.MarketBreadthPipelineService;
import org.factor_investing.quant_strategy.service.MarketBreadthQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market-breadth")
@Tag(name = "Market Breadth", description = "NIFTY breadth snapshots, sectors, and recalculation")
public class MarketBreadthController {
    private final MarketBreadthQueryService queryService;
    private final MarketBreadthPipelineService pipelineService;

    public MarketBreadthController(MarketBreadthQueryService queryService,
                                    MarketBreadthPipelineService pipelineService) {
        this.queryService = queryService;
        this.pipelineService = pipelineService;
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the latest persisted breadth snapshot")
    public BreadthSnapshotResponse latest(
            @RequestParam(defaultValue = "NIFTY500") NiftyIndexName universe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return queryService.latest(universe, asOf);
    }

    @GetMapping("/history")
    @Operation(summary = "Get chronological breadth history with a bounded response size")
    public List<BreadthSnapshotResponse> history(
            @RequestParam(defaultValue = "NIFTY500") NiftyIndexName universe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "500") int limit) {
        return queryService.history(universe, from, to, limit);
    }

    @GetMapping("/sectors")
    @Operation(summary = "Get per-sector 50-day EMA participation from the latest snapshot")
    public SectorBreadthResponse sectors(
            @RequestParam(defaultValue = "NIFTY500") NiftyIndexName universe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return queryService.sectors(universe, asOf);
    }

    @PostMapping("/recalculate")
    @Operation(summary = "Recalculate and idempotently persist one date or a date range")
    public BreadthRecalculationResponse recalculate(
            @Valid @RequestBody BreadthRecalculationRequest request) {
        return pipelineService.recalculate(request);
    }
}
