package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumConstants;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumResult;
import org.factor_investing.quant_strategy.strategies.momentum.StockMomentumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/momentum")
public class StockMomentumController {

    @Autowired
    private StockMomentumService momentumService;

    /**
     * Calculate momentum for all stocks
     */
    @PostMapping("/calculate-initial-momentum-data")
    public ResponseEntity<MomentumResult> calculateMomentum() {
        MomentumResult result = momentumService.calculateMomentum();

        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Update momentum rankings in the database
     */
@PostMapping("/UpdateRankings")
    public ResponseEntity<String> updateMomentumRankings() {
        try {
            momentumService.assignRanks();
            return ResponseEntity.ok("Momentum rankings updated successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update momentum rankings.");
        }
    }
}