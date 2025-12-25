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
import org.factor_investing.quant_strategy.util.JsonUtility;
import org.springframework.web.bind.annotation.*;

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
        boolean isStockData = Arrays.stream(fileParts).anyMatch(p -> p.equalsIgnoreCase("STOCK"));

        String filePath = STR."json/\{fileName}.json";
        List<JGetHistoricalCandleResponse> historicalData = upstoxHistoricalDataService.loadHistoricalJsonData(filePath);
        if (historicalData == null || historicalData.isEmpty()) {
            log.warn("No historical data found for file: {}", filePath);
            return "No historical data found";
        }

        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = historicalData.stream()
                .collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));

        List<StockPricesJson> existingList = stockPriceDataRepository.findAll();
        List<NSEStockMasterData> nseStockMasterData = nseStockMasterDataRepository.findAll();
        List<NSE_ETFMasterData> nseIndexDataMasterData = nse_indexDataRepository.findAll();

        List<StockPricesJson> toSave = new ArrayList<>();

        for (Map.Entry<String, List<JGetHistoricalCandleResponse.CandleData>> entry : stockData.entrySet()) {
            String symbol = entry.getKey();
            List<JGetHistoricalCandleResponse.CandleData> candleDataList = entry.getValue();

            // find existing entry safely (use findFirst\(\) on the stream)
            StockPricesJson stockPricesJson = existingList.stream()
                    .filter(spj -> {
                        if (spj == null) return false;
                        if (spj.getNseStockMasterData() != null && spj.getNseStockMasterData().getSymbol() != null) {
                            if (spj.getNseStockMasterData().getSymbol().equalsIgnoreCase(symbol)) return true;
                        }
                        if (spj.getNseETFMasterData() != null && spj.getNseETFMasterData().getSymbol() != null) {
                            if (spj.getNseETFMasterData().getSymbol().equalsIgnoreCase(symbol)) return true;
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);

            if (stockPricesJson == null) {
                stockPricesJson = new StockPricesJson();
            }

            if (isStockData) {
                NSEStockMasterData stockMasterData = nseStockMasterData.stream()
                        .filter(d -> d.getSymbol() != null && d.getSymbol().equalsIgnoreCase(symbol))
                        .findFirst()
                        .orElse(null);
                stockPricesJson.setNseStockMasterData(stockMasterData);
                stockPricesJson.setNseDataType(AssetDataType.STOCK);
            } else {
                NSE_ETFMasterData etfMasterData = nseIndexDataMasterData.stream()
                        .filter(d -> d.getSymbol() != null && d.getSymbol().equalsIgnoreCase(symbol))
                        .findFirst()
                        .orElse(null);
                stockPricesJson.setNseETFMasterData(etfMasterData);
                stockPricesJson.setNseDataType(AssetDataType.INDEX);
            }

            List<OHLCV> ohlcvDataList = candleDataList.stream().map(c -> {
                OHLCV o = new OHLCV();
                o.setDate(c.getPriceDate());
                o.setOpen(c.getOpen());
                o.setHigh(c.getHigh());
                o.setLow(c.getLow());
                o.setClose(c.getClose());
                o.setVolume(c.getVolume());
                return o;
            }).collect(Collectors.toList());

            stockPricesJson.setOhlcvData(ohlcvDataList);
            toSave.add(stockPricesJson);
        }

        stockPriceDataRepository.saveAll(toSave);
        log.info("Created stock price data list with {} entries.", toSave.size());
        return STR."Successfully saved stock price data to DB with size: \{toSave.size()}";
    }

}
