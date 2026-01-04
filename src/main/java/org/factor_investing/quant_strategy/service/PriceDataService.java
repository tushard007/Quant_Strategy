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
import java.util.List;
import java.util.Map;
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

        List<NSEStockMasterData> stockDataList = nseStockDataService.getAllStockData();
        for (NSEStockMasterData stockData : stockDataList) {
            String instrumentKey = STR."NSE_EQ|\{stockData.getIsinNumber()}";
            String stockName = stockData.getNameOfCompany();
            log.info("Fetching historical candle data for stock: {}", stockName);
            // Sleep for 3 seconds after every 400 calls to avoid rate limiting
            if (stockDataList.indexOf(stockData) % 400 == 0 && stockDataList.indexOf(stockData) != 0) {
                try {
                    Thread.sleep(300); // Sleep for 3 seconds after every 500 calls
                } catch (InterruptedException e) {
                    log.error("Thread interrupted while sleeping", e);
                }
            }
            GetHistoricalCandleResponse response = null;
            if (PriceFrequencey.WEEKLY.equals(PriceFrequencey.valueOf(timeFrame.name()))) {
                response = upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, toDate, fromDate);
            }
            if (PriceFrequencey.DAILY.equals(PriceFrequencey.valueOf(timeFrame.name()))) {
                response = upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "days", 1, toDate, fromDate);
            }

            if (response != null) {
                log.info("Successfully fetched data for stock: {}", stockName);
                result.add(getJavaObjectHistoricalData(response, stockData.getNameOfCompany(), stockData.getSymbol()));
            } else {
                log.warn("No data found for stock: {}", stockName);
            }
        }

        List<JGetHistoricalCandleResponse> historicalData = result.stream().toList();
        if (historicalData.isEmpty()) {
            log.warn("No historical data found for etf");
        }

        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = historicalData.stream()
                .collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));

        List<StockPricesJson> existingList = stockPriceDataRepository.findByNseDataType(AssetDataType.STOCK);

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
                            return spj.getNseETFMasterData().getSymbol().equalsIgnoreCase(symbol);
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);

            if (stockPricesJson == null) {
                stockPricesJson = new StockPricesJson();
            }
            NSEStockMasterData stockMasterData = stockDataList.stream()
                    .filter(d -> d.getSymbol() != null && d.getSymbol().equalsIgnoreCase(symbol))
                    .findFirst()
                    .orElse(null);
            stockPricesJson.setNseStockMasterData(stockMasterData);
            stockPricesJson.setNseDataType(AssetDataType.STOCK);
            stockPricesJson.setTimeFrame(PriceFrequencey.valueOf(timeFrame.name()));
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
        return "Successfully saved stock price data to DB with size: "+toSave.size();
    }

    public String saveOrUpdateETFPriceData(PriceFrequencey timeFrame) throws ParseException {

        List<JGetHistoricalCandleResponse> result = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        currentDate = DateUtil.getFridayDateIfWeekend(currentDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String toDate = currentDate.format(formatter);

        LocalDate beforeYearDate = DateUtil.getDateBeforeYear(currentDate, 1);
        beforeYearDate = DateUtil.getFridayDateIfWeekend(beforeYearDate);
        String fromDate = beforeYearDate.format(formatter);

            List<NSE_ETFMasterData>  indexDataList = upstoxHistoricalDataService.getNSEIndexData();
            for (NSE_ETFMasterData indexData : indexDataList) {
                String instrumentKey = "NSE_EQ|"+indexData.getIsinNumber();
                String stockName = indexData.getSecurityName();
                log.info("Fetching historical candle data for Index: {}", stockName);
                // Sleep for 3 seconds after every 100 calls to avoid rate limiting
                if (indexDataList.indexOf(indexData) % 100 == 0 && indexDataList.indexOf(indexData) != 0) {
                    try {
                        Thread.sleep(300); // Sleep for 3 seconds after every 500 calls
                    } catch (InterruptedException e) {
                        log.error("Thread interrupted while sleeping", e);
                    }
                }
                GetHistoricalCandleResponse response =null;
                if(PriceFrequencey.WEEKLY.equals(PriceFrequencey.valueOf(timeFrame.name()))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, toDate, fromDate);
                }
                if(PriceFrequencey.DAILY.equals(PriceFrequencey.valueOf(timeFrame.name()))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "days", 1, toDate, fromDate);
                }

                if (response != null) {
                    log.info("Successfully fetched data for index: {}", stockName);
                    result.add(getJavaObjectHistoricalData(response, indexData.getSecurityName(), indexData.getSymbol()));
                } else {
                    log.warn("No data found for index: {}", stockName);
                }
            }

        List<JGetHistoricalCandleResponse> historicalData = result.stream().toList();
        if (historicalData.isEmpty()) {
            log.warn("No historical data found");
        }

        Map<String, List<JGetHistoricalCandleResponse.CandleData>> stockData = historicalData.stream()
                .collect(Collectors.toMap(JGetHistoricalCandleResponse::getSymbol, JGetHistoricalCandleResponse::getData));

        List<StockPricesJson> existingList = stockPriceDataRepository.findByNseDataType(AssetDataType.ETF);

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
                            return spj.getNseETFMasterData().getSymbol().equalsIgnoreCase(symbol);
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);

            if (stockPricesJson == null) {
                stockPricesJson = new StockPricesJson();
            }
         NSE_ETFMasterData nseETFMasterData  = indexDataList.stream()
                    .filter(d -> d.getSymbol() != null && d.getSymbol().equalsIgnoreCase(symbol))
                    .findFirst()
                    .orElse(null);
            stockPricesJson.setNseETFMasterData(nseETFMasterData);
            stockPricesJson.setNseDataType(AssetDataType.ETF);
            stockPricesJson.setTimeFrame(PriceFrequencey.valueOf(timeFrame.name()));
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
        log.info("Created etf price data list with {} entries.", toSave.size());
        return STR."Successfully saved stock price data to DB with size: \{toSave.size()}";

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
