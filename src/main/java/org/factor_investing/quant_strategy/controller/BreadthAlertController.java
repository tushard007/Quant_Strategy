package org.factor_investing.quant_strategy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.BreadthAlertResponse;
import org.factor_investing.quant_strategy.service.BreadthAlertService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market-breadth/alerts")
@Tag(name = "Market Breadth Alerts", description = "Persisted in-app breadth transition alerts")
public class BreadthAlertController {
    private final BreadthAlertService alertService;

    public BreadthAlertController(BreadthAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List in-app breadth alerts with optional filters")
    public List<BreadthAlertResponse> history(
            @RequestParam(required = false) NiftyIndexName universe,
            @RequestParam(required = false) BreadthAlertType alertType,
            @RequestParam(required = false) AlertDeliveryState deliveryState,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit) {
        return alertService.history(universe, alertType, deliveryState, from, to, limit);
    }
}
