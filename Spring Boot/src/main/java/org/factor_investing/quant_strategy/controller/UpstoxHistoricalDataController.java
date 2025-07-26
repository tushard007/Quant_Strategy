package org.factor_investing.quant_strategy.controller;

import com.upstox.api.GetHistoricalCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.NSE_StockMasterData;
import org.factor_investing.quant_strategy.service.UpstoxHistoricalDataService;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

@GetMapping("/get-weekly-candle-data")
    private List<JGetHistoricalCandleResponse> getHistoricalWeeklyCandleData() throws InterruptedException {
    List<JGetHistoricalCandleResponse> result = new ArrayList<>();
    List<NSE_StockMasterData> stockDataList = upstoxHistoricalDataService.getNSEStockData().stream().limit(1000).toList() ;

        for (NSE_StockMasterData stockData : stockDataList) {
            String instrumentKey = STR."NSE_EQ|\{stockData.getIsinNumber()}";
            log.info("Fetching historical candle data for instrument: {}", instrumentKey);
           // Thread.sleep(3000); // Add 3 seconds delay between API calls
            GetHistoricalCandleResponse response = upstoxHistoricalDataService.getHistoricalCandleData(instrumentKey, "weeks", 1, "2025-07-01", "2025-01-01");
            if (response != null) {
                log.info("Successfully fetched data for instrument: {}", instrumentKey);
                result.add(getJavaObjectHistoricalData(response, stockData.getNameOfCompany()));
            } else {
                log.warn("No data found for instrument: {}", instrumentKey);
            }
        }
        upstoxHistoricalDataService.saveHistoricalJsonData(result);
    return result;
    }

    public JGetHistoricalCandleResponse getJavaObjectHistoricalData(GetHistoricalCandleResponse apiResult,String stockName) {
        JGetHistoricalCandleResponse convert = new JGetHistoricalCandleResponse();
        convert.setNameOfCompany(stockName);
        // Create new list for candle data
        List<JGetHistoricalCandleResponse.CandleData> candleDataList = new ArrayList<>();

        // Convert each HistoricalCandleData to CandleData
        if (apiResult.getData() != null) {
            for (List<Object> candleObj : apiResult.getData().getCandles()) {
                String timeStamp = candleObj.get(0).toString();
                Double open = Double.parseDouble(candleObj.get(1).toString());
                Double high = Double.parseDouble(candleObj.get(2).toString());
                Double low = Double.parseDouble(candleObj.get(3).toString());
                Double close = Double.parseDouble(candleObj.get(4).toString());
                double doubleValue = Double.parseDouble(candleObj.get(5).toString());
                Long volume = (long) doubleValue;

                JGetHistoricalCandleResponse.CandleData candleData = new JGetHistoricalCandleResponse.CandleData();
                candleData.setTimestamp(timeStamp);
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
