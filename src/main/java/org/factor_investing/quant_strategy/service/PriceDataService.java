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
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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

        LocalDate beforeYearDate = DateUtil.getDateBeforeYear(currentDate, 2);
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
        sanitizeStoredStockPrices(existingList);

        /*
         * Existing DB records map
         */
        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getTimeFrame() == timeFrame)
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

            stockPricesJson.setOhlcvData(
                    mergeOhlcvData(stockPricesJson.getOhlcvData(), candleDataList)
            );

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

        LocalDate historyStartDate = DateUtil.getFridayDateIfWeekend(
                DateUtil.getDateBeforeYear(currentDate, 2));

        List<StockPricesJson> existingList =
                stockPriceDataRepository.findAll();
        sanitizeStoredStockPrices(existingList);

        if (existingList == null || existingList.isEmpty()) {

            log.info("No existing stock price data found. Running full daily stock price update.");

            return saveOrUpdateStockPriceData(timeFrame);
        }

        Map<String, StockPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getTimeFrame() == timeFrame)
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

            Optional<LocalDate> firstPriceDate =
                    getFirstPriceDate(stockPricesJson);

            if (lastPriceDate.isPresent()
                    && !lastPriceDate.get().isBefore(currentDate)
                    && firstPriceDate.isPresent()
                    && !firstPriceDate.get().isAfter(historyStartDate)) {

                skippedCount++;

                continue;
            }

            String fromDate =
                    historyStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String instrumentKey = "NSE_EQ|" + stockData.getIsinNumber();

            log.info(
                    "Fetching rolling two-year stock price update for stock: {} from {} to {} ({}/{})",
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

        LocalDate historyStartDate = DateUtil.getFridayDateIfWeekend(
                DateUtil.getDateBeforeYear(currentDate, 2));

        List<ETFPricesJson> existingList =
                etfPriceDataRepository.findAll();
        sanitizeStoredETFPrices(existingList);

        if (existingList == null || existingList.isEmpty()) {

            log.info("No existing ETF price data found. Running full daily ETF price update.");

            return saveOrUpdateETFPriceData(timeFrame);
        }

        Map<String, ETFPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getTimeFrame() == timeFrame)
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

            Optional<LocalDate> firstPriceDate =
                    getFirstPriceDate(etfPricesJson);

            if (lastPriceDate.isPresent()
                    && !lastPriceDate.get().isBefore(currentDate)
                    && firstPriceDate.isPresent()
                    && !firstPriceDate.get().isAfter(historyStartDate)) {

                skippedCount++;

                continue;
            }

            String fromDate =
                    historyStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String instrumentKey = "NSE_EQ|" + indexData.getIsinNumber();

            log.info(
                    "Fetching rolling two-year ETF price update for ETF: {} from {} to {} ({}/{})",
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

        List<IndexPricesJson> existingList = indexPriceDataRepository.findAll();
        sanitizeStoredIndexPrices(existingList);
        Map<String, IndexPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getTimeFrame() == timeFrame)
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
        List<List<OHLCV>> priceSeries = List.of();

        if (AssetDataType.STOCK == assetDataType) {
            List<StockPricesJson> records = stockPriceDataRepository.findAll();
            sanitizeStoredStockPrices(records);
            priceSeries = records.stream()
                    .filter(item -> item.getTimeFrame() == PriceFrequencey.DAILY)
                    .map(StockPricesJson::getOhlcvData)
                    .toList();
        }

        if (AssetDataType.ETF == assetDataType) {
            List<ETFPricesJson> records = etfPriceDataRepository.findAll();
            sanitizeStoredETFPrices(records);
            priceSeries = records.stream()
                    .filter(item -> item.getTimeFrame() == PriceFrequencey.DAILY)
                    .map(ETFPricesJson::getOhlcvData)
                    .toList();
        }

        if (AssetDataType.INDEX == assetDataType) {
            List<IndexPricesJson> records = indexPriceDataRepository.findAll();
            sanitizeStoredIndexPrices(records);
            priceSeries = records.stream()
                    .filter(item -> item.getTimeFrame() == PriceFrequencey.DAILY)
                    .map(IndexPricesJson::getOhlcvData)
                    .toList();
        }

        boolean updatedTillCurrentTradingDate = coversRollingTwoYearWindow(priceSeries, currentDate);

        log.info(
                "{} price data covers the rolling two-year window ending {}: {}",
                assetDataType,
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

    private Optional<LocalDate> getFirstPriceDate(StockPricesJson stockPricesJson) {
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
                .min(Comparator.naturalOrder());
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

    private Optional<LocalDate> getFirstPriceDate(ETFPricesJson etfPricesJson) {
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
                .min(Comparator.naturalOrder());
    }

    private boolean coversRollingTwoYearWindow(List<List<OHLCV>> priceSeries, LocalDate currentDate) {
        LocalDate requiredStartDate = DateUtil.getFridayDateIfWeekend(
                DateUtil.getDateBeforeYear(currentDate, 2));
        LocalDate earliestDate = null;
        LocalDate latestDate = null;

        for (List<OHLCV> prices : priceSeries) {
            if (prices == null) {
                continue;
            }
            for (OHLCV price : prices) {
                if (price == null || price.getDate() == null) {
                    continue;
                }
                LocalDate priceDate = DateUtil.convertDateToLocalDate(price.getDate());
                if (earliestDate == null || priceDate.isBefore(earliestDate)) {
                    earliestDate = priceDate;
                }
                if (latestDate == null || priceDate.isAfter(latestDate)) {
                    latestDate = priceDate;
                }
            }
        }

        return earliestDate != null
                && latestDate != null
                && !earliestDate.isAfter(requiredStartDate)
                && !latestDate.isBefore(currentDate);
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

        List<OHLCV> merged = mergedDataByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        validateUniqueTradingDates(merged);
        return merged;
    }

    private void sanitizeStoredStockPrices(List<StockPricesJson> records) {
        List<StockPricesJson> changed = records.stream().filter(Objects::nonNull).filter(record -> {
            List<OHLCV> normalized = normalizeOhlcvData(record.getOhlcvData());
            if (size(record.getOhlcvData()) == normalized.size()) return false;
            log.warn("Removed duplicate stock trading dates: symbol={}, timeframe={}, before={}, after={}",
                    record.getNseStockMasterData() == null ? "unknown" : record.getNseStockMasterData().getSymbol(),
                    record.getTimeFrame(), size(record.getOhlcvData()), normalized.size());
            record.setOhlcvData(normalized); return true;
        }).toList();
        if (!changed.isEmpty()) stockPriceDataRepository.saveAll(changed);
    }

    private void sanitizeStoredETFPrices(List<ETFPricesJson> records) {
        List<ETFPricesJson> changed = records.stream().filter(Objects::nonNull).filter(record -> {
            List<OHLCV> normalized = normalizeOhlcvData(record.getOhlcvData());
            if (size(record.getOhlcvData()) == normalized.size()) return false;
            log.warn("Removed duplicate ETF trading dates: symbol={}, timeframe={}, before={}, after={}",
                    record.getNseETFMasterData() == null ? "unknown" : record.getNseETFMasterData().getSymbol(),
                    record.getTimeFrame(), size(record.getOhlcvData()), normalized.size());
            record.setOhlcvData(normalized); return true;
        }).toList();
        if (!changed.isEmpty()) etfPriceDataRepository.saveAll(changed);
    }

    private void sanitizeStoredIndexPrices(List<IndexPricesJson> records) {
        List<IndexPricesJson> changed = records.stream().filter(Objects::nonNull).filter(record -> {
            List<OHLCV> normalized = normalizeOhlcvData(record.getOhlcvData());
            if (size(record.getOhlcvData()) == normalized.size()) return false;
            log.warn("Removed duplicate index trading dates: symbol={}, timeframe={}, before={}, after={}",
                    record.getNseIndexMasterData() == null ? "unknown" : record.getNseIndexMasterData().getSymbol(),
                    record.getTimeFrame(), size(record.getOhlcvData()), normalized.size());
            record.setOhlcvData(normalized); return true;
        }).toList();
        if (!changed.isEmpty()) indexPriceDataRepository.saveAll(changed);
    }

    List<OHLCV> normalizeOhlcvData(List<OHLCV> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        NavigableMap<LocalDate, OHLCV> unique = new TreeMap<>();
        source.stream().filter(Objects::nonNull).filter(bar -> bar.getDate() != null)
                .forEach(bar -> unique.put(DateUtil.convertDateToLocalDate(bar.getDate()), bar));
        List<OHLCV> normalized = new ArrayList<>(unique.values());
        validateUniqueTradingDates(normalized);
        return normalized;
    }

    void validateUniqueTradingDates(List<OHLCV> rows) {
        long uniqueDates = rows.stream().filter(Objects::nonNull).filter(row -> row.getDate() != null)
                .map(row -> DateUtil.convertDateToLocalDate(row.getDate())).distinct().count();
        if (rows.size() != uniqueDates) {
            throw new IllegalStateException("OHLCV validation failed: row count " + rows.size()
                    + " does not match unique trading-date count " + uniqueDates);
        }
    }

    private int size(List<OHLCV> rows) { return rows == null ? 0 : rows.size(); }

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

        LocalDate beforeYearDate = DateUtil.getDateBeforeYear(currentDate, 2);

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
        sanitizeStoredETFPrices(existingList);

        /*
         * Existing ETF DB records map
         */
        Map<String, ETFPricesJson> existingMap =
                existingList.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getTimeFrame() == timeFrame)
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

            etfPricesJson.setOhlcvData(
                    mergeOhlcvData(etfPricesJson.getOhlcvData(), candleDataList)
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
