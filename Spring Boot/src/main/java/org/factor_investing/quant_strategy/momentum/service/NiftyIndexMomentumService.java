package org.factor_investing.quant_strategy.momentum.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.service.NiftyIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NiftyIndexMomentumService {
    @Autowired
    private NiftyIndexService niftyIndexService;
    @Autowired
    private TopMomentumStockService topMomentumStockService;

    public List<TopN_MomentumStock> getNiftyIndexMomentumStocks(String indexName, Date endDate) {
        List<String> stockList = getNiftyIndexStockList(indexName);
        return getMomentumStocksList(stockList, endDate);
    }

    public List<String> getNiftyIndexStockList(String indexName) {
        List<String> stockList = new ArrayList<>();
        switch (indexName) {
            case "Nifty50":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("Nifty50");
                break;
            case "NiftyNext50":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("NiftyNext50");
                break;
            case "NiftyMidcap150":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("NiftyMidcap150");
                break;
            case "NiftySmallcap250":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("NiftySmallcap250");
                break;
            case "Nifty500":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("Nifty500");
                break;
            case "Nifty750":
                stockList = niftyIndexService.getNiftyIndexDataMap().get("Nifty750");
                break;
        }
        return stockList;
    }

    public List<TopN_MomentumStock> getMomentumStocksList(List<String> stockList, Date endDate) {
        List<TopN_MomentumStock> topN_momentumStocks = topMomentumStockService.getMomentumStockByRebalenceStrategy(RebalenceStrategy.Monthly);

        Map<Date, List<TopN_MomentumStock>> topNStockGroupByRebalenceDdate=new LinkedHashMap<>();
        topNStockGroupByRebalenceDdate = topN_momentumStocks.stream()
                .sorted(Comparator.comparing(TopN_MomentumStock::getEndDate)) // Ensure ordering by date
                .collect(Collectors.groupingBy(
                        TopN_MomentumStock::getEndDate, // Key mapper: use endDate as the key
                        LinkedHashMap::new,             // Ensure insertion order with LinkedHashMap
                        Collectors.toList()             // Value mapper: list of stocks
                ));

        List<TopN_MomentumStock> momentumStocks = topNStockGroupByRebalenceDdate.get(endDate);


        List<TopN_MomentumStock> matchingMomentumStocks = momentumStocks.stream()
                .filter(momentumStock -> stockList.contains(momentumStock.getStockName()))
                .sorted(Comparator.comparing(TopN_MomentumStock::getPercentageReturn12Months).reversed())
                .toList();

        log.info("Matching Momentum Stocks size: {}", matchingMomentumStocks.size());
        return matchingMomentumStocks;
    }

}
