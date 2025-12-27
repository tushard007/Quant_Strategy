package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumResult;
import org.factor_investing.quant_strategy.strategies.momentum.StockMomentumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/momentum")
public class StockMomentumController {

    private final StockMomentumService momentumService;

    public StockMomentumController(StockMomentumService momentumService) {
        this.momentumService = momentumService;
    }

    /**
     * Calculate momentum for all stocks
     */
    @PostMapping("/calculate-initial-momentum-data/{assetDataType}")
    public ResponseEntity<MomentumResult> calculateMomentum(@PathVariable AssetDataType assetDataType) {
        MomentumResult result = momentumService.calculateMomentum(assetDataType);

        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Update momentum rankings in the database
     */
@PostMapping("/UpdateRankings/{assetDataType}")
    public ResponseEntity<String> updateMomentumRankings(@PathVariable AssetDataType assetDataType) {
        try {
            momentumService.assignRanks(assetDataType);
            return ResponseEntity.ok("Momentum rankings updated successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update momentum rankings.");
        }
    }
}