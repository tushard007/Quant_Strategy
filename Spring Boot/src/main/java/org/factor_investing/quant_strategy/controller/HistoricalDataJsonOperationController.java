package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.util.JsonUtility;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/v1")
@Slf4j
public class HistoricalDataJsonOperationController {
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;

    public HistoricalDataJsonOperationController(UpstoxHistoricalDataService upstoxHistoricalDataService) {
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
    }

    @GetMapping("/load-historical-json-data/{fileName}")
    public Map<String, List<JGetHistoricalCandleResponse.CandleData>> loadHistoricalJsonData(@PathVariable String fileName) {
        String filePath = STR."src/main/resources/json/\{fileName}";
        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = new LinkedHashMap<>();
        List<JGetHistoricalCandleResponse> historicalData = upstoxHistoricalDataService.loadHistoricalJsonData(filePath);
        if (historicalData != null && !historicalData.isEmpty()) {
            stockData = historicalData.stream()
                    .collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));
            log.info("Created stock data map with {} entries.", stockData.size());

        } else {
            log.warn("No historical data found in JSON file.");
        }

        return stockData;
    }

    @DeleteMapping("/clear-historical-json-data")
    public String clearHistoricalJsonData() {
        JsonUtility jsonUtility = new JsonUtility();
        Boolean isContentDeleted = jsonUtility.clearJsonDataInFiles();
        if (isContentDeleted) {
            log.info("Successfully deleted JSON file content");
            return "File Content deleted successfully";
        } else {
            log.error("Failed to delete JSON file content");
            return "File Content deletion failed";
        }
    }
}
