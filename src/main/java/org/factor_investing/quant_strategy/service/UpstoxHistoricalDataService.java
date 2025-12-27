package org.factor_investing.quant_strategy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.upstox.ApiException;
import com.upstox.api.GetHistoricalCandleResponse;
import io.swagger.client.api.HistoryV3Api;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.model.response.JGetHistoricalCandleResponse;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.JsonUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UpstoxHistoricalDataService {

    private final NSE_StockDataService nseStockDataService;
    private final NSE_IndexDataService nseIndexDataService;
    private final JsonUtility jsonUtility;

    @Autowired
    public UpstoxHistoricalDataService(JsonUtility jsonUtility, NSE_IndexDataService nseIndexDataService,
                                       NSE_StockDataService nseStockDataService, StockDataRepository stockPriceDataRepository) {
        this.nseIndexDataService = nseIndexDataService;
        this.nseStockDataService = nseStockDataService;
        this.jsonUtility = jsonUtility;
    }

    public GetHistoricalCandleResponse getHistoricalCandleData(String instrumentKey, String timeFrame, int interval, String toDate, String fromDate) {

        HistoryV3Api historyV3Api = new HistoryV3Api();
        GetHistoricalCandleResponse result = null;
        try {
            result = historyV3Api.getHistoricalCandleData1(instrumentKey, timeFrame, interval, toDate, fromDate);

        } catch (ApiException e) {
           log.error("Exception when calling HistoryV3Api->getHistoricalCandleData1");
        }
        return result;
    }

    public List<NSEStockMasterData> getNSEStockData() {
        return nseStockDataService.getAllStockData();

    }

    public List<NSE_ETFMasterData> getNSEIndexData() {
        return nseIndexDataService.getAllIndexData();
    }

    public void saveHistoricalJsonData(List<JGetHistoricalCandleResponse> data,String prefix) {
        String filePath = jsonUtility.writeToJson(data, prefix);
        log.info("Data saved to {}", filePath);
    }

    public List<JGetHistoricalCandleResponse> loadHistoricalJsonData(String filePath) {
        return jsonUtility.readFromJson(filePath,
                new TypeReference<List<JGetHistoricalCandleResponse>>() {
                });
    }
}