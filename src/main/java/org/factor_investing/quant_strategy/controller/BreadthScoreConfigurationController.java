package org.factor_investing.quant_strategy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.factor_investing.quant_strategy.model.request.BreadthScoreConfigurationRequest;
import org.factor_investing.quant_strategy.model.response.BreadthScoreConfigurationResponse;
import org.factor_investing.quant_strategy.service.BreadthScoreConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-breadth/config")
@Tag(name = "Market Breadth Configuration", description = "Versioned Breadth Score configuration")
public class BreadthScoreConfigurationController {
    private final BreadthScoreConfigurationService configurationService;

    public BreadthScoreConfigurationController(BreadthScoreConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    @Operation(summary = "Get the active Breadth Score configuration")
    public BreadthScoreConfigurationResponse active() {
        return configurationService.active();
    }

    @PutMapping
    @Operation(summary = "Create and activate a new immutable configuration version")
    public BreadthScoreConfigurationResponse createVersion(
            @Valid @RequestBody BreadthScoreConfigurationRequest request) {
        return configurationService.createVersion(request);
    }
}
