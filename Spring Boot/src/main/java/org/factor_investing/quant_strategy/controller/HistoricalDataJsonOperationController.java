package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.factor_investing.quant_strategy.repository.NSE_ETFMasterDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
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
    private final StockDataRepository stockPriceDataRepository;
    private final NSEStockMasterDataRepository nseStockMasterDataRepository;
    private final NSE_ETFMasterDataRepository nse_indexDataRepository;


    public HistoricalDataJsonOperationController(UpstoxHistoricalDataService upstoxHistoricalDataService, StockDataRepository stockPriceDataRepository, NSEStockMasterDataRepository nseStockMasterDataRepository, NSE_ETFMasterDataRepository nseIndexDataRepository) {
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
        this.stockPriceDataRepository = stockPriceDataRepository;
        this.nseStockMasterDataRepository = nseStockMasterDataRepository;
        this.nse_indexDataRepository = nseIndexDataRepository;
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
        return stockData.get(symbol).stream()
                .filter(candleData -> candleData.getPriceDate().compareTo(forDate) == 1)
                .map(JGetHistoricalCandleResponse.CandleData::getClose)
                .findFirst()
                .orElse(null);
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
        String[] fileParts = fileName.split("_");
        var ref = new Object() {
            boolean isStockData = false;
        };
        for (String part : fileParts) {
            if (part.equalsIgnoreCase("STOCK")) {
                ref.isStockData = true;
                break;
            }
            }
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
            log.info("Time taken to load historical data to DB records: {} seconds", durationInSeconds2);

            List<StockPricesJson> stockPriceDataList = new ArrayList<>();
            List<NSEStockMasterData> nseStockMasterData = nseStockMasterDataRepository.findAll();
            List<NSE_ETFMasterData> nseIndexDataMasterData = nse_indexDataRepository.findAll();
            stockData.forEach(
                    (symbol, candleDataList) -> {
                        List<OHLCV> ohlcvDataList = new ArrayList<>();
                        StockPricesJson stockPricesJson = new StockPricesJson();
                        if (ref.isStockData) {
                            NSEStockMasterData stockMasterData = nseStockMasterData.stream().filter(data -> data.getSymbol().equalsIgnoreCase(symbol)).findFirst().orElse(null);
                            stockPricesJson.setNseStockMasterData(stockMasterData);
                            stockPricesJson.setNsseDataType(AssetDataType.STOCK);
                        }
                        else {
                            NSE_ETFMasterData etfMasterData = nseIndexDataMasterData.stream().filter(data -> data.getSymbol().equalsIgnoreCase(symbol)).findFirst().orElse(null);
                            stockPricesJson.setNse_etfMasterData(etfMasterData);
                            stockPricesJson.setNsseDataType(AssetDataType.INDEX);
                        }
                        for (JGetHistoricalCandleResponse.CandleData candleData : candleDataList) {
                            OHLCV ohlcv = new OHLCV();
                            ohlcv.setDate(candleData.getPriceDate());
                            ohlcv.setOpen(candleData.getOpen());
                            ohlcv.setHigh(candleData.getHigh());
                            ohlcv.setLow(candleData.getLow());
                            ohlcv.setClose(candleData.getClose());
                            ohlcv.setVolume(candleData.getVolume());
                            ohlcvDataList.add(ohlcv);
                        }
                        stockPricesJson.setOhlcvData(ohlcvDataList);
                        stockPriceDataList.add(stockPricesJson);
                    }
            );
            stockPriceDataRepository.saveAll(stockPriceDataList);
            log.info("Created stock price data list with {} entries.", stockPriceDataList.size());
            return STR."Successfully saved stock price data to DB with size: \{stockPriceDataList.size()}";
        }
}
