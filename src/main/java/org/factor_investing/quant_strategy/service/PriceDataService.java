package org.factor_investing.quant_strategy.service;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.*;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PriceDataService {

    private final StockDataRepository stockPriceDataRepository;
    private final NSE_StockDataService nseStockDataService;
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;

    public PriceDataService(StockDataRepository stockPriceDataRepository, NSE_StockDataService nseStockDataService,
                            UpstoxHistoricalDataService upstoxHistoricalDataService) {
        this.stockPriceDataRepository = stockPriceDataRepository;
        this.nseStockDataService = nseStockDataService;
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
    }

    public String saveOrUpdateStockPriceData(PriceFrequencey timeFrame) throws ParseException {

        List<JGetHistoricalCandleResponse> result = new ArrayList<>();

        LocalDate currentDate = LocalDate.now();
        currentDate = DateUtil.getFridayDateIfWeekend(currentDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String toDate = currentDate.format(formatter);

        LocalDate beforeYearDate = DateUtil.getDateBeforeYear(currentDate, 1);
        beforeYearDate = DateUtil.getFridayDateIfWeekend(beforeYearDate);

        String fromDate = beforeYearDate.format(formatter);

        String interval = PriceFrequencey.WEEKLY.equals(timeFrame) ? "weeks" : "days";

        List<NSEStockMasterData> stockDataList = nseStockDataService.getAllStockData();

        /*
         * Create stock map once to avoid O(n²) lookup
         */
        Map<String, NSEStockMasterData> stockMap =
                stockDataList.stream()
                        .filter(s -> s.getSymbol() != null)
                        .collect(Collectors.toMap(
                                s -> s.getSymbol().toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        /*
         * Sequential processing with throttling
         */
        for (int i = 0; i < stockDataList.size(); i++) {

            NSEStockMasterData stockData = stockDataList.get(i);

            String instrumentKey = "NSE_EQ|" + stockData.getIsinNumber();

            String stockName = stockData.getNameOfCompany();

            log.info("Fetching historical candle data for stock: {} ({}/{})", stockName, i + 1, stockDataList.size());

            /*
             * Batch cooldown
             */
            if (i > 0 && i % 200 == 0) {

                try {

                    log.info("Cooling down after {} API calls", i);

                    Thread.sleep(5000);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    log.error("Thread interrupted during cooldown", e);
                }
            }

            GetHistoricalCandleResponse response =
                    fetchHistoricalDataWithRetry(
                            instrumentKey,
                            interval,
                            toDate,
                            fromDate
                    );

            /*
             * Small delay after every request
             */
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (response != null
                    && response.getData() != null
                    && response.getData().getCandles() != null
                    && !response.getData().getCandles().isEmpty()) {

                log.info("Successfully fetched data for stock: {}", stockName);

                result.add(getJavaObjectHistoricalData(response, stockData.getNameOfCompany(), stockData.getSymbol()));

            } else {
                log.warn("No data found for stock: {}", stockName);
            }
        }

        List<JGetHistoricalCandleResponse> historicalData = result.stream().toList();

        if (historicalData.isEmpty()) {

            log.warn("No historical data found for stocks");

            return "No stock historical data found";
        }

        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockDataMap =
                historicalData.stream()
                        .collect(Collectors.toMap(
                                JGetHistoricalCandleResponse::getSymbol,
                                JGetHistoricalCandleResponse::getData
                        ));

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findByNseDataType(AssetDataType.STOCK);

        /*
         * Existing DB records map
         */
        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(spj ->
                                spj.getNseStockMasterData() != null
                                        && spj.getNseStockMasterData().getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                spj -> spj.getNseStockMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<StockPricesJson> toSave = new ArrayList<>();

        for (Map.Entry<String,
                List<JGetHistoricalCandleResponse.CandleData>> entry : stockDataMap.entrySet()) {

            String symbol = entry.getKey();

            List<JGetHistoricalCandleResponse.CandleData> candleDataList = entry.getValue();

            StockPricesJson stockPricesJson =
                    existingMap.getOrDefault(
                            symbol.toLowerCase(),
                            new StockPricesJson()
                    );

            NSEStockMasterData stockMasterData =
                    stockMap.get(symbol.toLowerCase());

            stockPricesJson.setNseStockMasterData(stockMasterData);

            stockPricesJson.setNseDataType(AssetDataType.STOCK);

            stockPricesJson.setTimeFrame(timeFrame);

            List<OHLCV> ohlcvDataList =
                    candleDataList.stream()
                            .map(c -> {

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

        log.info("Created stock price data list with {} entries.", toSave.size()
        );

        return "Successfully saved stock price data to DB with size: " + toSave.size();
    }

    public String updateStockPriceDataFromLastDate() throws ParseException {

        PriceFrequencey timeFrame = PriceFrequencey.DAILY;

        LocalDate currentDate = DateUtil.getFridayDateIfWeekend(LocalDate.now());

        String toDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String interval = "days";

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findByNseDataType(AssetDataType.STOCK);

        if (existingList == null || existingList.isEmpty()) {

            log.info("No existing stock price data found. Running full daily stock price update.");

            return saveOrUpdateStockPriceData(timeFrame);
        }

        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(spj ->
                                spj.getNseStockMasterData() != null
                                        && spj.getNseStockMasterData().getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                spj -> spj.getNseStockMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<NSEStockMasterData> stockDataList = nseStockDataService.getAllStockData();

        List<StockPricesJson> toSave = new ArrayList<>();

        int skippedCount = 0;

        for (int i = 0; i < stockDataList.size(); i++) {

            NSEStockMasterData stockData = stockDataList.get(i);

            if (stockData.getSymbol() == null) {

                continue;
            }

            StockPricesJson stockPricesJson =
                    existingMap.get(stockData.getSymbol().toLowerCase());

            Optional<LocalDate> lastPriceDate =
                    getLastPriceDate(stockPricesJson);

            if (lastPriceDate.isPresent()
                    && !lastPriceDate.get().isBefore(currentDate)) {

                skippedCount++;

                continue;
            }

            LocalDate fromLocalDate =
                    lastPriceDate.orElse(DateUtil.getDateBeforeYear(currentDate, 1));

            fromLocalDate = DateUtil.getFridayDateIfWeekend(fromLocalDate);

            String fromDate =
                    fromLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String instrumentKey = "NSE_EQ|" + stockData.getIsinNumber();

            log.info(
                    "Fetching startup stock price update for stock: {} from {} to {} ({}/{})",
                    stockData.getNameOfCompany(),
                    fromDate,
                    toDate,
                    i + 1,
                    stockDataList.size()
            );

            if (i > 0 && i % 200 == 0) {

                try {

                    log.info("Cooling down after {} startup stock API calls", i);

                    Thread.sleep(5000);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    log.error("Thread interrupted during startup stock update cooldown", e);

                    break;
                }
            }

            GetHistoricalCandleResponse response =
                    fetchHistoricalDataWithRetry(
                            instrumentKey,
                            interval,
                            toDate,
                            fromDate
                    );

            try {

                Thread.sleep(300);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                log.error("Thread interrupted during startup stock update delay", e);

                break;
            }

            if (response == null
                    || response.getData() == null
                    || response.getData().getCandles() == null
                    || response.getData().getCandles().isEmpty()) {

                log.warn("No startup update data found for stock: {}", stockData.getNameOfCompany());

                continue;
            }

            JGetHistoricalCandleResponse historicalData =
                    getJavaObjectHistoricalData(
                            response,
                            stockData.getNameOfCompany(),
                            stockData.getSymbol()
                    );

            StockPricesJson updatedStockPricesJson =
                    stockPricesJson != null ? stockPricesJson : new StockPricesJson();

            updatedStockPricesJson.setNseStockMasterData(stockData);

            updatedStockPricesJson.setNseDataType(AssetDataType.STOCK);

            updatedStockPricesJson.setTimeFrame(timeFrame);

            updatedStockPricesJson.setOhlcvData(
                    mergeOhlcvData(
                            updatedStockPricesJson.getOhlcvData(),
                            historicalData.getData()
                    )
            );

            toSave.add(updatedStockPricesJson);
        }

        if (!toSave.isEmpty()) {

            stockPriceDataRepository.saveAll(toSave);
        }

        log.info(
                "Startup stock price update completed. Updated: {}, skipped current: {}",
                toSave.size(),
                skippedCount
        );

        return "Startup stock price update completed. Updated: "
                + toSave.size()
                + ", skipped current: "
                + skippedCount;
    }

    public String updateETFPriceDataFromLastDate() throws ParseException {

        PriceFrequencey timeFrame = PriceFrequencey.DAILY;

        LocalDate currentDate = DateUtil.getFridayDateIfWeekend(LocalDate.now());

        String toDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String interval = "days";

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findByNseDataType(AssetDataType.ETF);

        if (existingList == null || existingList.isEmpty()) {

            log.info("No existing ETF price data found. Running full daily ETF price update.");

            return saveOrUpdateETFPriceData(timeFrame);
        }

        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(spj ->
                                spj.getNseETFMasterData() != null
                                        && spj.getNseETFMasterData().getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                spj -> spj.getNseETFMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<NSE_ETFMasterData> indexDataList = upstoxHistoricalDataService.getNSEIndexData();

        List<StockPricesJson> toSave = new ArrayList<>();

        int skippedCount = 0;

        for (int i = 0; i < indexDataList.size(); i++) {

            NSE_ETFMasterData indexData = indexDataList.get(i);

            if (indexData.getSymbol() == null) {

                continue;
            }

            StockPricesJson stockPricesJson =
                    existingMap.get(indexData.getSymbol().toLowerCase());

            Optional<LocalDate> lastPriceDate =
                    getLastPriceDate(stockPricesJson);

            if (lastPriceDate.isPresent()
                    && !lastPriceDate.get().isBefore(currentDate)) {

                skippedCount++;

                continue;
            }

            LocalDate fromLocalDate =
                    lastPriceDate.orElse(DateUtil.getDateBeforeYear(currentDate, 1));

            fromLocalDate = DateUtil.getFridayDateIfWeekend(fromLocalDate);

            String fromDate =
                    fromLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String instrumentKey = "NSE_EQ|" + indexData.getIsinNumber();

            log.info(
                    "Fetching startup ETF price update for ETF: {} from {} to {} ({}/{})",
                    indexData.getSecurityName(),
                    fromDate,
                    toDate,
                    i + 1,
                    indexDataList.size()
            );

            if (i > 0 && i % 150 == 0) {

                try {

                    log.info("Cooling down after {} startup ETF API calls", i);

                    Thread.sleep(5000);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    log.error("Thread interrupted during startup ETF update cooldown", e);

                    break;
                }
            }

            GetHistoricalCandleResponse response =
                    fetchHistoricalDataWithRetry(
                            instrumentKey,
                            interval,
                            toDate,
                            fromDate
                    );

            try {

                Thread.sleep(200);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                log.error("Thread interrupted during startup ETF update delay", e);

                break;
            }

            if (response == null
                    || response.getData() == null
                    || response.getData().getCandles() == null
                    || response.getData().getCandles().isEmpty()) {

                log.warn("No startup update data found for ETF: {}", indexData.getSecurityName());

                continue;
            }

            JGetHistoricalCandleResponse historicalData =
                    getJavaObjectHistoricalData(
                            response,
                            indexData.getSecurityName(),
                            indexData.getSymbol()
                    );

            StockPricesJson updatedStockPricesJson =
                    stockPricesJson != null ? stockPricesJson : new StockPricesJson();

            updatedStockPricesJson.setNseETFMasterData(indexData);

            updatedStockPricesJson.setNseDataType(AssetDataType.ETF);

            updatedStockPricesJson.setTimeFrame(timeFrame);

            updatedStockPricesJson.setOhlcvData(
                    mergeOhlcvData(
                            updatedStockPricesJson.getOhlcvData(),
                            historicalData.getData()
                    )
            );

            toSave.add(updatedStockPricesJson);
        }

        if (!toSave.isEmpty()) {

            stockPriceDataRepository.saveAll(toSave);
        }

        log.info(
                "Startup ETF price update completed. Updated: {}, skipped current: {}",
                toSave.size(),
                skippedCount
        );

        return "Startup ETF price update completed. Updated: "
                + toSave.size()
                + ", skipped current: "
                + skippedCount;
    }

    public boolean isPriceDataUpdatedTillCurrentTradingDate(AssetDataType assetDataType) {

        LocalDate currentDate = DateUtil.getFridayDateIfWeekend(LocalDate.now());

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findByNseDataType(assetDataType);

        if (existingList == null || existingList.isEmpty()) {

            return false;
        }

        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(stockPricesJson -> getAssetSymbol(stockPricesJson, assetDataType) != null)
                        .collect(Collectors.toMap(
                                stockPricesJson -> getAssetSymbol(stockPricesJson, assetDataType).toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        if (AssetDataType.STOCK == assetDataType) {

            return nseStockDataService.getAllStockData().stream()
                    .filter(stockData -> stockData.getSymbol() != null)
                    .allMatch(stockData ->
                            isAssetUpdatedTillCurrentTradingDate(
                                    existingMap.get(stockData.getSymbol().toLowerCase()),
                                    currentDate
                            )
                    );
        }

        return upstoxHistoricalDataService.getNSEIndexData().stream()
                .filter(indexData -> indexData.getSymbol() != null)
                .allMatch(indexData ->
                        isAssetUpdatedTillCurrentTradingDate(
                                existingMap.get(indexData.getSymbol().toLowerCase()),
                                currentDate
                        )
                );
    }

    private String getAssetSymbol(StockPricesJson stockPricesJson, AssetDataType assetDataType) {

        if (AssetDataType.STOCK == assetDataType
                && stockPricesJson.getNseStockMasterData() != null) {

            return stockPricesJson.getNseStockMasterData().getSymbol();
        }

        if (AssetDataType.ETF == assetDataType
                && stockPricesJson.getNseETFMasterData() != null) {

            return stockPricesJson.getNseETFMasterData().getSymbol();
        }

        return null;
    }

    private boolean isAssetUpdatedTillCurrentTradingDate(
            StockPricesJson stockPricesJson,
            LocalDate currentDate
    ) {

        return getLastPriceDate(stockPricesJson)
                .map(lastPriceDate -> !lastPriceDate.isBefore(currentDate))
                .orElse(false);
    }

    private Optional<LocalDate> getLastPriceDate(StockPricesJson stockPricesJson) {

        if (stockPricesJson == null
                || stockPricesJson.getOhlcvData() == null
                || stockPricesJson.getOhlcvData().isEmpty()) {

            return Optional.empty();
        }

        return stockPricesJson.getOhlcvData().stream()
                .filter(Objects::nonNull)
                .map(OHLCV::getDate)
                .filter(Objects::nonNull)
                .map(DateUtil::convertDateToLocalDate)
                .max(Comparator.naturalOrder());
    }

    private List<OHLCV> mergeOhlcvData(
            List<OHLCV> existingData,
            List<JGetHistoricalCandleResponse.CandleData> newData
    ) {

        Map<LocalDate, OHLCV> mergedDataByDate =
                new LinkedHashMap<>();

        if (existingData != null) {

            for (OHLCV ohlcv : existingData) {

                if (ohlcv != null && ohlcv.getDate() != null) {

                    mergedDataByDate.put(
                            DateUtil.convertDateToLocalDate(ohlcv.getDate()),
                            ohlcv
                    );
                }
            }
        }

        if (newData != null) {

            for (JGetHistoricalCandleResponse.CandleData candleData : newData) {

                if (candleData == null || candleData.getPriceDate() == null) {

                    continue;
                }

                OHLCV ohlcv = new OHLCV();

                Date priceDate = candleData.getPriceDate();

                ohlcv.setDate(priceDate);
                ohlcv.setOpen(candleData.getOpen());
                ohlcv.setHigh(candleData.getHigh());
                ohlcv.setLow(candleData.getLow());
                ohlcv.setClose(candleData.getClose());
                ohlcv.setVolume(candleData.getVolume());

                mergedDataByDate.put(
                        DateUtil.convertDateToLocalDate(priceDate),
                        ohlcv
                );
            }
        }

        return mergedDataByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }


    /*
     * Retry implementation with exponential backoff
     */
    private GetHistoricalCandleResponse fetchHistoricalDataWithRetry(
            String instrumentKey,
            String interval,
            String toDate,
            String fromDate
    ) {

        int maxRetries = 5;

        int retry = 0;

        while (retry < maxRetries) {
            try {
                return upstoxHistoricalDataService
                        .getHistoricalCandleData(
                                instrumentKey,
                                interval,
                                1,
                                toDate,
                                fromDate
                        );

            } catch (Exception e) {
                log.error("Unexpected exception for instrument={}", instrumentKey, e);
                return null;
            }
        }

        log.error("Max retries exceeded for instrument={}", instrumentKey);

        return null;
    }

    public String saveOrUpdateETFPriceData(PriceFrequencey timeFrame)
            throws ParseException {

        List<JGetHistoricalCandleResponse> result =
                new ArrayList<>();

        LocalDate currentDate = LocalDate.now();

        currentDate = DateUtil.getFridayDateIfWeekend(currentDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String toDate = currentDate.format(formatter);

        LocalDate beforeYearDate = DateUtil.getDateBeforeYear(currentDate, 1);

        beforeYearDate = DateUtil.getFridayDateIfWeekend(beforeYearDate);

        String fromDate = beforeYearDate.format(formatter);

        String interval = PriceFrequencey.WEEKLY.equals(timeFrame) ? "weeks" : "days";

        List<NSE_ETFMasterData> indexDataList = upstoxHistoricalDataService.getNSEIndexData();

        /*
         * ETF map for O(1) lookup
         */
        Map<String, NSE_ETFMasterData> etfMap =
                indexDataList.stream()
                        .filter(e -> e.getSymbol() != null)
                        .collect(Collectors.toMap(
                                e -> e.getSymbol().toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        /*
         * Sequential processing with throttling
         */
        for (int i = 0; i < indexDataList.size(); i++) {

            NSE_ETFMasterData indexData = indexDataList.get(i);

            String instrumentKey = "NSE_EQ|" + indexData.getIsinNumber();

            String stockName = indexData.getSecurityName();

            log.info("Fetching historical candle data for ETF: {} ({}/{})", stockName, i + 1, indexDataList.size()
            );

            /*
             * Batch cooldown after every 150 requests
             */
            if (i > 0 && i % 150 == 0) {

                try {

                    log.info("Cooling down after {} ETF API calls", i);

                    Thread.sleep(5000);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    log.error("Thread interrupted during cooldown", e);
                }
            }

            GetHistoricalCandleResponse response =
                    fetchHistoricalDataWithRetry(
                            instrumentKey,
                            interval,
                            toDate,
                            fromDate
                    );

            /*
             * Small delay after every request
             */
            try {

                Thread.sleep(200);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            }

            if (response != null
                    && response.getData() != null
                    && response.getData().getCandles() != null
                    && !response.getData().getCandles().isEmpty()) {

                log.info("Successfully fetched data for ETF: {}", stockName);

                result.add(
                        getJavaObjectHistoricalData(
                                response,
                                indexData.getSecurityName(),
                                indexData.getSymbol()
                        )
                );

            } else {

                log.warn("No data found for ETF: {}", stockName);
            }
        }

        List<JGetHistoricalCandleResponse> historicalData = result.stream().toList();

        if (historicalData.isEmpty()) {

            log.warn("No ETF historical data found");

            return "No ETF historical data found";
        }

        Map<String,
                List<JGetHistoricalCandleResponse.CandleData>>
                stockDataMap =
                historicalData.stream()
                        .collect(Collectors.toMap(
                                JGetHistoricalCandleResponse::getSymbol,
                                JGetHistoricalCandleResponse::getData
                        ));

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findByNseDataType(
                        AssetDataType.ETF
                );

        /*
         * Existing ETF DB records map
         */
        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(spj ->
                                spj.getNseETFMasterData() != null
                                        && spj.getNseETFMasterData()
                                        .getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                spj -> spj.getNseETFMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<StockPricesJson> toSave =
                new ArrayList<>();

        for (Map.Entry<String,
                List<JGetHistoricalCandleResponse.CandleData>>
                entry : stockDataMap.entrySet()) {

            String symbol = entry.getKey();

            List<JGetHistoricalCandleResponse.CandleData>
                    candleDataList = entry.getValue();

            StockPricesJson stockPricesJson =
                    existingMap.getOrDefault(
                            symbol.toLowerCase(),
                            new StockPricesJson()
                    );

            NSE_ETFMasterData nseETFMasterData =
                    etfMap.get(symbol.toLowerCase());

            stockPricesJson.setNseETFMasterData(
                    nseETFMasterData
            );

            stockPricesJson.setNseDataType(
                    AssetDataType.ETF
            );

            stockPricesJson.setTimeFrame(timeFrame);

            List<OHLCV> ohlcvDataList =
                    candleDataList.stream()
                            .map(c -> {

                                OHLCV o = new OHLCV();

                                o.setDate(c.getPriceDate());
                                o.setOpen(c.getOpen());
                                o.setHigh(c.getHigh());
                                o.setLow(c.getLow());
                                o.setClose(c.getClose());
                                o.setVolume(c.getVolume());

                                return o;

                            }).collect(Collectors.toList());

            stockPricesJson.setOhlcvData(
                    ohlcvDataList
            );

            toSave.add(stockPricesJson);
        }

        stockPriceDataRepository.saveAll(toSave);

        log.info(
                "Created ETF price data list with {} entries.",
                toSave.size()
        );

        return "Successfully saved ETF price data to DB with size: "
                + toSave.size();
    }

    public JGetHistoricalCandleResponse getJavaObjectHistoricalData(GetHistoricalCandleResponse apiResult, String stockName, String symbol) throws ParseException {
        JGetHistoricalCandleResponse convert = new JGetHistoricalCandleResponse();
        convert.setFullName(stockName);
        convert.setSymbol(symbol);

        List<JGetHistoricalCandleResponse.CandleData> candleDataList = new ArrayList<>();
        // Convert each HistoricalCandleData to CandleData
        if (apiResult.getData() != null) {
            for (List<Object> candleObj : apiResult.getData().getCandles()) {
                String priceDateTimeStamp = candleObj.get(0).toString();
                Double open = Double.parseDouble(candleObj.get(1).toString());
                Double high = Double.parseDouble(candleObj.get(2).toString());
                Double low = Double.parseDouble(candleObj.get(3).toString());
                Double close = Double.parseDouble(candleObj.get(4).toString());
                double doubleValue = Double.parseDouble(candleObj.get(5).toString());
                Long volume = (long) doubleValue;

                JGetHistoricalCandleResponse.CandleData candleData = new JGetHistoricalCandleResponse.CandleData();
                candleData.setPriceDate(DateUtil.timeStampToDate(priceDateTimeStamp));
                candleData.setOpen(open);
                candleData.setHigh(high);
                candleData.setLow(low);
                candleData.setClose(close);
                candleData.setVolume(volume);
                candleDataList.add(candleData);
            }
        }
        convert.setData(candleDataList);
        convert.setStatus(String.valueOf(apiResult.getStatus()));
        return convert;
    }

}
