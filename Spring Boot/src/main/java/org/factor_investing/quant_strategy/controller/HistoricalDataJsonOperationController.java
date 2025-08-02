package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.StockPriceData;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.repository.StockPriceDataRepository;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.factor_investing.quant_strategy.util.JsonUtility;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/v1")
@Slf4j
public class HistoricalDataJsonOperationController {
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;
    private final StockPriceDataRepository stockPriceDataRepository;

    public HistoricalDataJsonOperationController(UpstoxHistoricalDataService upstoxHistoricalDataService, StockPriceDataRepository stockPriceDataRepository) {
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
        this.stockPriceDataRepository = stockPriceDataRepository;

    }

    @GetMapping("/load-historical-json-data/{fileName}")
    public Map<String, List<JGetHistoricalCandleResponse.CandleData>> loadHistoricalJsonData(@PathVariable String fileName) {
        String filePath = "json/" + fileName;
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

    @GetMapping("/get-close-price/{fileName}/{symbol}/{date}")
    public Double getClosePricebySymbolandDate(@PathVariable String fileName, @PathVariable String symbol, @PathVariable String date) throws ParseException {
        String filePath = "json/" + fileName;
        List<JGetHistoricalCandleResponse> historicalData = upstoxHistoricalDataService.loadHistoricalJsonData(filePath);
        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = new LinkedHashMap<>();
        stockData = historicalData.stream().collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));
        Date forDate = DateUtil.stringToDate(date);
        //return close price of stock based on stock symbol and date
        Double closePrice = stockData.get(symbol).stream()
                .filter(candleData -> candleData.getPriceDate().compareTo(forDate) == 1)
                .map(JGetHistoricalCandleResponse.CandleData::getClose)
                .findFirst()
                .orElse(null);
        return closePrice;
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
@PostMapping("/save-historical-json-data/{fileName}")
    public String saveJsonDataToDB(@PathVariable String fileName) {
    long startTime = System.nanoTime();
    List<JGetHistoricalCandleResponse> historicalData = upstoxHistoricalDataService.loadHistoricalJsonData(STR."json/\{fileName}.json");
    long endTime = System.nanoTime();
    long durationInNano = endTime - startTime;
    double durationInSeconds = (double) durationInNano / 1_000_000_000.0; // Convert to seconds
    log.info("Time taken to load historical data from JSON file: {} seconds", durationInSeconds);
    Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = new LinkedHashMap<>();
        stockData = historicalData.stream().collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));
    long endTime2 = System.nanoTime();
    long durationInNano2 = endTime2 - startTime;
    double durationInSeconds2 = (double) durationInNano2 / 1_000_000_000.0; // Convert to seconds
    log.info("Time taken to load historical data to DB 10 records: {} seconds", durationInSeconds2);

    List<StockPriceData> stockPriceDataList= new ArrayList<>();

        stockData.forEach(
                (symbol, candleDataList) -> {
                    for (JGetHistoricalCandleResponse.CandleData candleData : candleDataList) {
                        StockPriceData stockPriceData = new StockPriceData();
                        stockPriceData.setSymbol(symbol);
                        stockPriceData.setDate(new java.sql.Date(candleData.getPriceDate().getTime()));
                        stockPriceData.setOpen(candleData.getOpen());
                        stockPriceData.setHigh(candleData.getHigh());
                        stockPriceData.setLow(candleData.getLow());
                        stockPriceData.setClose(candleData.getClose());
                        stockPriceData.setVolume(candleData.getVolume());
                        stockPriceDataList.add(stockPriceData);
                    }
                }
        );
        stockPriceDataRepository.saveAll(stockPriceDataList);
        log.info("Created stock price data list with {} entries.", stockPriceDataList.size());
        return STR."Successfully saved stock price data to DB with size: \{stockPriceDataList.size()}";
    }
}
