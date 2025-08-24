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
    @PostMapping("/calculate")
    public ResponseEntity<MomentumResult> calculateMomentum() {
        MomentumResult result = momentumService.calculateMomentum();

        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Get only momentum stock names (top stocks only)
     */
    @PostMapping("/names")
    public ResponseEntity<List<String>> getMomentumStockNames() {
        try {
            List<String> stockNames = momentumService.getMomentumStocksList();
            return ResponseEntity.ok(stockNames);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
@PostMapping("/UpdateRankings")
    public ResponseEntity<String> updateMomentumRankings() {
        try {
            momentumService.assignRanks();
            return ResponseEntity.ok("Momentum rankings updated successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update momentum rankings.");
        }
    }

    /**
     * Get momentum analysis summary
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMomentumSummary() {

        try {
            MomentumResult result = momentumService.calculateMomentum();

            if (result.isValid()) {
                Map<String, Object> summary = Map.of(
                        "totalAnalyzed", result.getTotalAnalyzed(),
                        "qualifiedCount", result.getQualifiedCount(),
                        "topStockCount", Math.min(result.getQualifiedCount(), MomentumConstants.TOP_NUMBER_MOMENTUM_STOCKS),
                        "topStocks", result.getTopStockNames(),
                        "bestStock", result.getQualifiedStocks().getFirst());
                return ResponseEntity.ok(summary);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}