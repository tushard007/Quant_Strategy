package org.factor_investing.quant_strategy.service;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.*;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.model.event.PriceDataChangedEvent;
import org.factor_investing.quant_strategy.repository.ETFPriceDataRepository;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.NSEIndexMasterDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

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

    /** Upstox V3 daily/weekly/monthly historical availability begins in January 2000. */
    private static final LocalDate MAXIMUM_HISTORY_START_DATE = LocalDate.of(2000, 1, 1);
    /** A single V3 daily request may cover at most one decade. */
    private static final int DAILY_REQUEST_MAX_YEARS = 10;

    private final StockDataRepository stockPriceDataRepository;
    private final ETFPriceDataRepository etfPriceDataRepository;
    private final IndexPriceDataRepository indexPriceDataRepository;
    private final NSEIndexMasterDataRepository nseIndexMasterDataRepository;
    private final NSE_StockDataService nseStockDataService;
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;
    private final ApplicationEventPublisher eventPublisher;
    private final PriceImportPersistence persistence;

    @org.springframework.beans.factory.annotation.Value("${price-data.import.chunk-size:25}")
    private int importChunkSize = 25;

    @org.springframework.beans.factory.annotation.Value("${price-data.import.request-delay-ms:300}")
    private long importRequestDelayMs = 300;


    public PriceDataService(
            StockDataRepository stockPriceDataRepository,
            ETFPriceDataRepository etfPriceDataRepository,
            IndexPriceDataRepository indexPriceDataRepository,
            NSEIndexMasterDataRepository nseIndexMasterDataRepository,
            NSE_StockDataService nseStockDataService,
            UpstoxHistoricalDataService upstoxHistoricalDataService,
            ApplicationEventPublisher eventPublisher,
            PriceImportPersistence persistence
    ) {
        this.stockPriceDataRepository = stockPriceDataRepository;
        this.etfPriceDataRepository = etfPriceDataRepository;
        this.indexPriceDataRepository = indexPriceDataRepository;
        this.nseIndexMasterDataRepository = nseIndexMasterDataRepository;
        this.nseStockDataService = nseStockDataService;
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
        this.eventPublisher = eventPublisher;
        this.persistence = persistence;
    }

    public String saveOrUpdateStockPriceData(PriceFrequencey timeFrame) throws ParseException {
        List<NSEStockMasterData> symbols = nseStockDataService.getAllStockData();
        return importPrices(symbols, timeFrame, AssetDataType.STOCK,
                NSEStockMasterData::getSymbol, NSEStockMasterData::getNameOfCompany,
                NSEStockMasterData::getIsinNumber,
                batch -> stockPriceDataRepository.findAllByTimeFrameAndNseStockMasterData_SymbolIn(timeFrame, batch),
                row -> row.getNseStockMasterData().getSymbol(), StockPricesJson::getOhlcvData,
                (master, existing, candles) -> {
                    StockPricesJson row = existing == null ? new StockPricesJson() : existing;
                    row.setNseStockMasterData(master);
                    row.setTimeFrame(timeFrame);
                    row.setOhlcvData(mergeOhlcvData(row.getOhlcvData(), candles));
                    return row;
                }, persistence::saveStocks);
    }

    public String updateStockPriceDataFromLastDate() throws ParseException {
        return saveOrUpdateStockPriceData(PriceFrequencey.DAILY);
    }

    public String updateETFPriceDataFromLastDate() throws ParseException {
        return saveOrUpdateETFPriceData(PriceFrequencey.DAILY);
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

        LocalDate beforeTwoYearDate = MAXIMUM_HISTORY_START_DATE;

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
            publishPriceDataChanged(AssetDataType.INDEX);
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

        boolean updatedTillCurrentTradingDate = coversCurrentTradingDate(priceSeries, currentDate);

        log.info(
                "{} price data is updated through current trading date {}: {}",
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

    private boolean coversCurrentTradingDate(List<List<OHLCV>> priceSeries, LocalDate currentDate) {
        boolean foundSeries = false;
        for (List<OHLCV> prices : priceSeries) {
            if (prices == null || prices.isEmpty()) return false;
            Optional<LocalDate> latestDate = prices.stream().filter(Objects::nonNull)
                    .map(OHLCV::getDate).filter(Objects::nonNull)
                    .map(DateUtil::convertDateToLocalDate).max(Comparator.naturalOrder());
            if (latestDate.isEmpty() || latestDate.get().isBefore(currentDate)) return false;
            foundSeries = true;
        }
        return foundSeries;
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
        if (!changed.isEmpty()) {
            stockPriceDataRepository.saveAll(changed);
            publishPriceDataChanged(AssetDataType.STOCK);
        }
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
        if (!changed.isEmpty()) {
            etfPriceDataRepository.saveAll(changed);
            publishPriceDataChanged(AssetDataType.ETF);
        }
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
        if (!changed.isEmpty()) {
            indexPriceDataRepository.saveAll(changed);
            publishPriceDataChanged(AssetDataType.INDEX);
        }
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

    private void publishPriceDataChanged(AssetDataType assetDataType) {
        if (eventPublisher != null) eventPublisher.publishEvent(new PriceDataChangedEvent(assetDataType));
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
        LocalDate requestedFrom = LocalDate.parse(fromDate);
        LocalDate requestedTo = LocalDate.parse(toDate);
        if (!"days".equals(interval) || requestedTo.isBefore(requestedFrom.plusYears(DAILY_REQUEST_MAX_YEARS))) {
            return fetchSingleHistoricalRangeWithRetry(instrumentKey, interval, toDate, fromDate);
        }

        GetHistoricalCandleResponse combined = null;
        LocalDate chunkFrom = requestedFrom;
        while (!chunkFrom.isAfter(requestedTo)) {
            LocalDate chunkTo = chunkFrom.plusYears(DAILY_REQUEST_MAX_YEARS).minusDays(1);
            if (chunkTo.isAfter(requestedTo)) chunkTo = requestedTo;
            log.info("Fetching Upstox daily history chunk for instrument={} from {} to {}",
                    instrumentKey, chunkFrom, chunkTo);
            GetHistoricalCandleResponse chunk = fetchSingleHistoricalRangeWithRetry(instrumentKey, interval,
                    chunkTo.toString(), chunkFrom.toString());
            if (chunk == null || chunk.getData() == null || chunk.getData().getCandles() == null) return null;
            if (chunk.getData().getCandles() != null) {
                if (combined == null) combined = chunk;
                else combined.getData().getCandles().addAll(chunk.getData().getCandles());
            }
            chunkFrom = chunkTo.plusDays(1);
            if (!chunkFrom.isAfter(requestedTo)) pauseBetweenHistoryChunks();
        }
        return combined;
    }

    private GetHistoricalCandleResponse fetchSingleHistoricalRangeWithRetry(
            String instrumentKey, String interval, String toDate, String fromDate) {

        int maxRetries = 5;
        for (int retry = 0; retry < maxRetries; retry++) {
            checkImportInterrupted();
            try {
                GetHistoricalCandleResponse response = upstoxHistoricalDataService
                        .getHistoricalCandleData(
                                instrumentKey,
                                interval,
                                1,
                                toDate,
                                fromDate
                        );
                if (response != null) return response;
            } catch (Exception e) {
                log.warn("Historical request attempt {} failed for instrument={} from {} to {}",
                        retry + 1, instrumentKey, fromDate, toDate, e);
            }
            if (retry + 1 < maxRetries) pauseBeforeRetry(retry);
        }
        log.error("Max retries exceeded for instrument={} from {} to {}", instrumentKey, fromDate, toDate);
        return null;
    }

    private void pauseBeforeRetry(int retry) {
        try {
            Thread.sleep(Math.min(4_000L, 500L * (1L << retry)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pauseBetweenHistoryChunks() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String saveOrUpdateETFPriceData(PriceFrequencey timeFrame) throws ParseException {
        List<NSE_ETFMasterData> symbols = upstoxHistoricalDataService.getNSEIndexData();
        return importPrices(symbols, timeFrame, AssetDataType.ETF,
                NSE_ETFMasterData::getSymbol, NSE_ETFMasterData::getSecurityName,
                NSE_ETFMasterData::getIsinNumber,
                batch -> etfPriceDataRepository.findAllByTimeFrameAndNseETFMasterData_SymbolIn(timeFrame, batch),
                row -> row.getNseETFMasterData().getSymbol(), ETFPricesJson::getOhlcvData,
                (master, existing, candles) -> {
                    ETFPricesJson row = existing == null ? new ETFPricesJson() : existing;
                    row.setNseETFMasterData(master);
                    row.setTimeFrame(timeFrame);
                    row.setOhlcvData(mergeOhlcvData(row.getOhlcvData(), candles));
                    return row;
                }, persistence::saveEtfs);
    }

    @FunctionalInterface
    private interface PriceMerger<M, P> {
        P merge(M master, P existing, List<JGetHistoricalCandleResponse.CandleData> candles) throws ParseException;
    }

    private <M, P> String importPrices(
            List<M> masters, PriceFrequencey timeFrame, AssetDataType asset,
            Function<M, String> symbol, Function<M, String> name, Function<M, String> isin,
            Function<List<String>, List<P>> load, Function<P, String> storedSymbol,
            Function<P, List<OHLCV>> bars, PriceMerger<M, P> merge,
            java.util.function.Consumer<List<P>> save) throws ParseException {
        if (importChunkSize < 1 || importRequestDelayMs < 0) {
            throw new IllegalArgumentException("Import chunk size must be positive and request delay nonnegative");
        }
        LocalDate to = DateUtil.getFridayDateIfWeekend(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        String interval = timeFrame == PriceFrequencey.WEEKLY ? "weeks" : "days";
        int saved = 0;
        List<String> failures = new ArrayList<>();
        // Only the current chunk's JSON histories are loaded into memory.
        try (var lock = persistence.acquireLock(asset, timeFrame)) {
            for (int offset = 0; offset < masters.size(); offset += importChunkSize) {
                checkImportInterrupted();
                lock.checkHeld();
                List<M> batch = masters.subList(offset, Math.min(offset + importChunkSize, masters.size()));
                Map<String, P> existing = load.apply(batch.stream().map(symbol).filter(Objects::nonNull).toList())
                        .stream().collect(Collectors.toMap(storedSymbol, Function.identity(), (first, second) -> first));
                List<P> pending = new ArrayList<>();
                for (M master : batch) {
                    checkImportInterrupted();
                    String ticker = symbol.apply(master);
                    if (ticker == null || isin.apply(master) == null) {
                        failures.add(ticker == null ? "<missing-symbol>" : ticker);
                        continue;
                    }
                    P row = existing.get(ticker);
                    LocalDate from = incrementalStart(row == null ? null : bars.apply(row), timeFrame);
                    if (from.isAfter(to)) continue;
                    log.info("Importing {} {} from {} to {}", asset, ticker, from, to);
                    GetHistoricalCandleResponse response = fetchHistoricalDataWithRetry(
                            "NSE_EQ|" + isin.apply(master), interval, to.toString(), from.toString());
                    if (response == null || response.getData() == null || response.getData().getCandles() == null) {
                        failures.add(ticker);
                    } else if (!response.getData().getCandles().isEmpty()) {
                        try {
                            pending.add(merge.merge(master, row,
                                    getJavaObjectHistoricalData(response, name.apply(master), ticker).getData()));
                        } catch (ParseException | RuntimeException exception) {
                            failures.add(ticker);
                            log.error("Cannot parse prices for {}", ticker, exception);
                        }
                    }
                    pauseImportRequests();
                }
                // The persistence bean commits a separate transaction before we fetch the next chunk.
                if (!pending.isEmpty()) {
                    lock.checkHeld();
                    try {
                        save.accept(pending);
                    } catch (RuntimeException exception) {
                        failures.addAll(pending.stream().map(storedSymbol).toList());
                        if (eventPublisher != null) eventPublisher.publishEvent(new PriceImportProgress(
                                offset + batch.size(), masters.size(), saved, List.copyOf(failures)));
                        throw exception;
                    }
                    saved += pending.size();
                }
                int processed = Math.min(offset + batch.size(), masters.size());
                if (eventPublisher != null) eventPublisher.publishEvent(
                        new PriceImportProgress(processed, masters.size(), saved, List.copyOf(failures)));
                log.info("{} import committed: processed={}/{}, saved={}, failed={}",
                        asset, processed, masters.size(), saved, failures.size());
            }
        } finally {
            // Refresh once, including after partial failure, instead of reloading all history per chunk.
            if (saved > 0) publishPriceDataChanged(asset);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Import partially completed; saved=" + saved + ", failed symbols=" + failures);
        }
        return "Successfully saved " + asset + " price data to DB with size: " + saved;
    }

    LocalDate incrementalStart(List<OHLCV> bars, PriceFrequencey timeFrame) {
        LocalDate latest = bars == null ? MAXIMUM_HISTORY_START_DATE : bars.stream()
                .filter(Objects::nonNull).map(OHLCV::getDate).filter(Objects::nonNull)
                .map(DateUtil::convertDateToLocalDate).max(Comparator.naturalOrder())
                .orElse(MAXIMUM_HISTORY_START_DATE);
        // Refresh the last bar; it may have been fetched before the session/week closed.
        return timeFrame == PriceFrequencey.WEEKLY && latest.isAfter(MAXIMUM_HISTORY_START_DATE)
                ? latest.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                : latest;
    }

    private void pauseImportRequests() {
        try {
            Thread.sleep(importRequestDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Price import interrupted; committed chunks are retained", exception);
        }
    }

    private void checkImportInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Price import interrupted; committed chunks are retained");
        }
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
