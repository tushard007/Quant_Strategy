package org.factor_investing.quant_strategy.controller;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("api/v1/upstox-historical-data")
public class UpstoxHistoricalDataController {
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;

    public UpstoxHistoricalDataController(UpstoxHistoricalDataService upstoxHistoricalDataService) {
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
    }

    @GetMapping("/get-candle-data/{timeFrame}/{nseDataType}")
    private List<JGetHistoricalCandleResponse> getHistoricalWeeklyCandleData(@PathVariable String timeFrame,@PathVariable String nseDataType) throws ParseException {
        List<JGetHistoricalCandleResponse> result = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        currentDate= DateUtil.getFridayDateIfWeekend(currentDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String toDate = currentDate.format(formatter);

        LocalDate beforeYearDate =  DateUtil.getDateBeforeYear(currentDate, 2);
        beforeYearDate = DateUtil.getFridayDateIfWeekend(beforeYearDate);
        String fromDate = beforeYearDate.format(formatter);

        if(AssetDataType.STOCK.equals(AssetDataType.valueOf(nseDataType))) {
            List<NSEStockMasterData> stockDataList = upstoxHistoricalDataService.getNSEStockData();
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
                GetHistoricalCandleResponse response =null;
                if(PriceFrequencey.WEEKLY.equals(PriceFrequencey.valueOf(timeFrame))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, toDate, fromDate);
                }
                if(PriceFrequencey.DAILY.equals(PriceFrequencey.valueOf(timeFrame))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "days", 1, toDate, fromDate);
                }

                if (response != null) {
                    log.info("Successfully fetched data for stock: {}", stockName);
                    result.add(getJavaObjectHistoricalData(response, stockData.getNameOfCompany(), stockData.getSymbol()));
                } else {
                    log.warn("No data found for stock: {}", stockName);
                }
            }
        }
        if (AssetDataType.INDEX.equals(AssetDataType.valueOf(nseDataType))) {
        List<NSE_ETFMasterData>  indexDataList = upstoxHistoricalDataService.getNSEIndexData();
            for (NSE_ETFMasterData indexData : indexDataList) {
                String instrumentKey = STR."NSE_EQ|\{indexData.getIsinNumber()}";
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
                if(PriceFrequencey.WEEKLY.equals(PriceFrequencey.valueOf(timeFrame))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, toDate, fromDate);
                }
                if(PriceFrequencey.DAILY.equals(PriceFrequencey.valueOf(timeFrame))) {
                    response= upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "days", 1, toDate, fromDate);
                }

                if (response != null) {
                    log.info("Successfully fetched data for index: {}", stockName);
                    result.add(getJavaObjectHistoricalData(response, indexData.getSecurityName(), indexData.getSymbol()));
                } else {
                    log.warn("No data found for index: {}", stockName);
                }
            }
        }

        upstoxHistoricalDataService.saveHistoricalJsonData(result, STR."\{timeFrame}_\{nseDataType}_historical_data");
        return result;
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
