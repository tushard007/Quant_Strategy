package org.factor_investing.quant_strategy.controller;


import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.strategies.alpha.JensensAlphaService;
import org.factor_investing.quant_strategy.strategies.alpha.StockAlphaVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alpha")
public class JensensAlphaController {

    @Autowired
    private JensensAlphaService alphaService;
    @Autowired
    private StockPriceCacheService stockPriceCacheService;

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, StockAlphaVO>> calculateAlpha() {
        Map<String, List<OHLCV>> stocksData = stockPriceCacheService.getAllStockPriceData();
        Map<String, StockAlphaVO> results = null;//alphaService.calculateJensensAlpha(stocksData);
        return ResponseEntity.ok(null);
    }

    @PostMapping("/batch")
    public LinkedHashMap<String, StockAlphaVO> calculateBatchAlpha(){
        Map<String, List<OHLCV>> stocksData = stockPriceCacheService.getAllStockPriceData();
Map<String, StockAlphaVO> results = null;
//        alphaService.calculateJensensAlpha(stocksData);
//        return results.entrySet()
//                .stream()
//                .sorted(Map.Entry.<String, StockAlphaVO>comparingByValue(
//                        (vo1, vo2) -> Double.compare(vo2.getAlpha(), vo1.getAlpha()) // Descending order
//                ))
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        Map.Entry::getValue,
//                        (e1, e2) -> e1, // Merge function (shouldn't be needed for unique keys)
//                        LinkedHashMap::new // Preserve insertion order
//                ));
        return null;

    }
}
