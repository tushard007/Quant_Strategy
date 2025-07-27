package org.factor_investing.quant_strategy.controller;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.NSE_StockMasterData;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("api/v1/upstox-historical-data")
public class UpstoxHistoricalDataController {
    private final UpstoxHistoricalDataService upstoxHistoricalDataService;

    public UpstoxHistoricalDataController(UpstoxHistoricalDataService upstoxHistoricalDataService) {
        this.upstoxHistoricalDataService = upstoxHistoricalDataService;
    }

    @GetMapping("/get-weekly-candle-data")
    private List<JGetHistoricalCandleResponse> getHistoricalWeeklyCandleData() throws ParseException {
        List<JGetHistoricalCandleResponse> result = new ArrayList<>();
        List<NSE_StockMasterData> stockDataList = upstoxHistoricalDataService.getNSEStockData().stream().limit(500).toList();

        for (NSE_StockMasterData stockData : stockDataList) {
            String instrumentKey = STR."NSE_EQ|\{stockData.getIsinNumber()}";
            log.info("Fetching historical candle data for instrument: {}", instrumentKey);
            // Thread.sleep(3000); // Add 3 seconds delay between API calls
            GetHistoricalCandleResponse response = upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, "2025-07-01", "2025-01-01");
            if (response != null) {
                log.info("Successfully fetched data for instrument: {}", instrumentKey);
                result.add(getJavaObjectHistoricalData(response, stockData.getNameOfCompany(), stockData.getSymbol()));
            } else {
                log.warn("No data found for instrument: {}", instrumentKey);
            }
        }
        upstoxHistoricalDataService.saveHistoricalJsonData(result);
        return result;
    }

    @GetMapping("/load-historical-json-data")
    public Map<String, List<JGetHistoricalCandleResponse.CandleData>>  loadHistoricalJsonData() {
        String filePath = "src/main/resources/json/Weekly_historical_data_20250727_104305.json";
        Map<String, List<JGetHistoricalCandleResponse.CandleData>>  stockData=new LinkedHashMap<>();
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
