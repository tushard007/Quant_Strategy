package org.factor_investing.quant_strategy.service;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.*;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.repository.ETFPriceDataRepository;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.NSEIndexMasterDataRepository;
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
    private final ETFPriceDataRepository etfPriceDataRepository;
    private final IndexPriceDataRepository indexPriceDataRepository;
    private final NSEIndexMasterDataRepository nseIndexMasterDataRepository;
    private final NSE_StockDataService nseStockDataService;
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;

    public PriceDataService(
            StockDataRepository stockPriceDataRepository,
            ETFPriceDataRepository etfPriceDataRepository,
            IndexPriceDataRepository indexPriceDataRepository,
            NSEIndexMasterDataRepository nseIndexMasterDataRepository,
            NSE_StockDataService nseStockDataService,
            UpstoxHistoricalDataService upstoxHistoricalDataService
    ) {
        this.stockPriceDataRepository = stockPriceDataRepository;
        this.etfPriceDataRepository = etfPriceDataRepository;
        this.indexPriceDataRepository = indexPriceDataRepository;
        this.nseIndexMasterDataRepository = nseIndexMasterDataRepository;
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
                stockPriceDataRepository.findAll();

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
                stockPriceDataRepository.findAll();

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

        List<ETFPricesJson> existingList =
                etfPriceDataRepository.findAll();

        if (existingList == null || existingList.isEmpty()) {

            log.info("No existing ETF price data found. Running full daily ETF price update.");

            return saveOrUpdateETFPriceData(timeFrame);
        }

        Map<String, ETFPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(etfPricesJson ->
                                etfPricesJson.getNseETFMasterData() != null
                                        && etfPricesJson.getNseETFMasterData().getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                etfPricesJson -> etfPricesJson.getNseETFMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<NSE_ETFMasterData> indexDataList = upstoxHistoricalDataService.getNSEIndexData();

        List<ETFPricesJson> toSave = new ArrayList<>();

        int skippedCount = 0;

        for (int i = 0; i < indexDataList.size(); i++) {

            NSE_ETFMasterData indexData = indexDataList.get(i);

            if (indexData.getSymbol() == null) {

                continue;
            }

            ETFPricesJson etfPricesJson =
                    existingMap.get(indexData.getSymbol().toLowerCase());

            Optional<LocalDate> lastPriceDate =
                    getLastPriceDate(etfPricesJson);

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

            ETFPricesJson updatedETFPricesJson =
                    etfPricesJson != null ? etfPricesJson : new ETFPricesJson();

            updatedETFPricesJson.setNseETFMasterData(indexData);

            updatedETFPricesJson.setTimeFrame(timeFrame);

            updatedETFPricesJson.setOhlcvData(
                    mergeOhlcvData(
                            updatedETFPricesJson.getOhlcvData(),
                            historicalData.getData()
                    )
            );

            toSave.add(updatedETFPricesJson);
        }

        if (!toSave.isEmpty()) {

            etfPriceDataRepository.saveAll(toSave);
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

    public String saveOrUpdateIndexPriceData(PriceFrequencey timeFrame)
            throws ParseException {

        List<NSEIndexMasterData> indexMasterDataList =
                nseIndexMasterDataRepository.findAll();

        if (indexMasterDataList.isEmpty()) {

            log.warn("No index master data found");

            return "No index master data found";
        }

        LocalDate currentDate = DateUtil.getFridayDateIfWeekend(LocalDate.now());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String toDate = currentDate.format(formatter);

        LocalDate beforeTwoYearDate =
                DateUtil.getFridayDateIfWeekend(DateUtil.getDateBeforeYear(currentDate, 2));

        String fromDate = beforeTwoYearDate.format(formatter);

        String interval = PriceFrequencey.WEEKLY.equals(timeFrame) ? "weeks" : "days";

        Map<String, IndexPricesJson> existingMap =
                indexPriceDataRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(indexPricesJson ->
                                indexPricesJson.getNseIndexMasterData() != null
                                        && indexPricesJson.getNseIndexMasterData().getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                indexPricesJson -> indexPricesJson.getNseIndexMasterData().getSymbol().toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<IndexPricesJson> toSave = new ArrayList<>();

        for (int i = 0; i < indexMasterDataList.size(); i++) {

            NSEIndexMasterData indexMasterData = indexMasterDataList.get(i);

            if (indexMasterData.getSymbol() == null
                    || indexMasterData.getInstrumentKey() == null) {

                log.warn(
                        "Skipping index master row with missing symbol or instrument key: {}",
                        indexMasterData.getIndexName()
                );

                continue;
            }

            log.info(
                    "Fetching historical candle data for index: {} ({}/{})",
                    indexMasterData.getIndexName(),
                    i + 1,
                    indexMasterDataList.size()
            );

            GetHistoricalCandleResponse response =
                    fetchHistoricalDataWithRetry(
                            indexMasterData.getInstrumentKey(),
                            interval,
                            toDate,
                            fromDate
                    );

            if (response == null
                    || response.getData() == null
                    || response.getData().getCandles() == null
                    || response.getData().getCandles().isEmpty()) {

                log.warn("No data found for index: {}", indexMasterData.getIndexName());

                continue;
            }

            JGetHistoricalCandleResponse historicalData =
                    getJavaObjectHistoricalData(
                            response,
                            indexMasterData.getIndexName(),
                            indexMasterData.getSymbol()
                    );

            IndexPricesJson indexPricesJson =
                    existingMap.getOrDefault(
                            indexMasterData.getSymbol().toLowerCase(),
                            new IndexPricesJson()
                    );

            indexPricesJson.setNseIndexMasterData(indexMasterData);
            indexPricesJson.setTimeFrame(timeFrame);
            indexPricesJson.setOhlcvData(
                    mergeOhlcvData(
                            indexPricesJson.getOhlcvData(),
                            historicalData.getData()
                    )
            );

            toSave.add(indexPricesJson);
        }

        if (!toSave.isEmpty()) {

            indexPriceDataRepository.saveAll(toSave);
        }

        log.info("Created index price data list with {} entries.", toSave.size());

        return "Successfully saved index price data to DB with size: " + toSave.size();
    }

    public boolean isPriceDataUpdatedTillCurrentTradingDate(AssetDataType assetDataType) {

        LocalDate currentDate = DateUtil.getFridayDateIfWeekend(LocalDate.now());
        Optional<LocalDate> latestUpdatedOnDate = Optional.empty();

        if (AssetDataType.STOCK == assetDataType) {

            latestUpdatedOnDate = getLatestUpdatedOnDate(stockPriceDataRepository.findAll());
        }

        if (AssetDataType.ETF == assetDataType) {

            latestUpdatedOnDate = getLatestETFUpdatedOnDate(etfPriceDataRepository.findAll());
        }

        if (AssetDataType.INDEX == assetDataType) {

            latestUpdatedOnDate = getLatestIndexUpdatedOnDate(indexPriceDataRepository.findAll());
        }

        boolean updatedTillCurrentTradingDate =
                latestUpdatedOnDate
                        .map(updatedOnDate -> updatedOnDate.isEqual(currentDate))
                        .orElse(false);

        log.info(
                "{} price latest updatedOn date: {}, current trading date: {}, updated: {}",
                assetDataType,
                latestUpdatedOnDate.map(LocalDate::toString).orElse("empty"),
                currentDate,
                updatedTillCurrentTradingDate
        );

        return updatedTillCurrentTradingDate;
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

    private Optional<LocalDate> getLastPriceDate(ETFPricesJson etfPricesJson) {

        if (etfPricesJson == null
                || etfPricesJson.getOhlcvData() == null
                || etfPricesJson.getOhlcvData().isEmpty()) {

            return Optional.empty();
        }

        return etfPricesJson.getOhlcvData().stream()
                .filter(Objects::nonNull)
                .map(OHLCV::getDate)
                .filter(Objects::nonNull)
                .map(DateUtil::convertDateToLocalDate)
                .max(Comparator.naturalOrder());
    }

    private Optional<LocalDate> getLatestUpdatedOnDate(List<StockPricesJson> stockPricesJsonList) {

        if (stockPricesJsonList == null || stockPricesJsonList.isEmpty()) {

            return Optional.empty();
        }

        return stockPricesJsonList.stream()
                .filter(Objects::nonNull)
                .map(StockPricesJson::getUpdatedOn)
                .filter(Objects::nonNull)
                .map(updatedOn -> updatedOn.toLocalDate())
                .max(Comparator.naturalOrder());
    }

    private Optional<LocalDate> getLatestETFUpdatedOnDate(List<ETFPricesJson> etfPricesJsonList) {

        if (etfPricesJsonList == null || etfPricesJsonList.isEmpty()) {

            return Optional.empty();
        }

        return etfPricesJsonList.stream()
                .filter(Objects::nonNull)
                .map(ETFPricesJson::getUpdatedOn)
                .filter(Objects::nonNull)
                .map(updatedOn -> updatedOn.toLocalDate())
                .max(Comparator.naturalOrder());
    }

    private Optional<LocalDate> getLatestIndexUpdatedOnDate(List<IndexPricesJson> indexPricesJsonList) {

        if (indexPricesJsonList == null || indexPricesJsonList.isEmpty()) {

            return Optional.empty();
        }

        return indexPricesJsonList.stream()
                .filter(Objects::nonNull)
                .map(IndexPricesJson::getUpdatedOn)
                .filter(Objects::nonNull)
                .map(updatedOn -> updatedOn.toLocalDate())
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

        List<ETFPricesJson> existingList =
                etfPriceDataRepository.findAll();

        /*
         * Existing ETF DB records map
         */
        Map<String, ETFPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(etfPricesJson ->
                                etfPricesJson.getNseETFMasterData() != null
                                        && etfPricesJson.getNseETFMasterData()
                                        .getSymbol() != null
                        )
                        .collect(Collectors.toMap(
                                etfPricesJson -> etfPricesJson.getNseETFMasterData()
                                        .getSymbol()
                                        .toLowerCase(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        List<ETFPricesJson> toSave =
                new ArrayList<>();

        for (Map.Entry<String,
                List<JGetHistoricalCandleResponse.CandleData>>
                entry : stockDataMap.entrySet()) {

            String symbol = entry.getKey();

            List<JGetHistoricalCandleResponse.CandleData>
                    candleDataList = entry.getValue();

            ETFPricesJson etfPricesJson =
                    existingMap.getOrDefault(
                            symbol.toLowerCase(),
                            new ETFPricesJson()
                    );

            NSE_ETFMasterData nseETFMasterData =
                    etfMap.get(symbol.toLowerCase());

            etfPricesJson.setNseETFMasterData(
                    nseETFMasterData
            );

            etfPricesJson.setTimeFrame(timeFrame);

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

            etfPricesJson.setOhlcvData(
                    ohlcvDataList
            );

            toSave.add(etfPricesJson);
        }

        etfPriceDataRepository.saveAll(toSave);

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
