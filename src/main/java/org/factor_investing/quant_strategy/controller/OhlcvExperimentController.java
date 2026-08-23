package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.response.OhlcvExperimentResult;
import org.factor_investing.quant_strategy.strategies.experiment.OhlcvExperimentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ohlcv-experiment")
public class OhlcvExperimentController {
    private final OhlcvExperimentService service;

    public OhlcvExperimentController(OhlcvExperimentService service) { this.service = service; }

    @PostMapping("/run/{assetDataType}")
    public OhlcvExperimentResult run(@PathVariable AssetDataType assetDataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        try { return service.runCached(assetDataType, asOfDate); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception); }
    }
}
