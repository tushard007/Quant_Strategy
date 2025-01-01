package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.NiftyIndexStock;
import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.factor_investing.quant_strategy.momentum.service.NiftyIndexMomentumService;
import org.factor_investing.quant_strategy.service.NiftyIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nifty-index")
public class NiftyIndexController {
    @Autowired
    private NiftyIndexService niftyIndexService;
    @Autowired
    private NiftyIndexMomentumService niftyIndexMomentumService;
    @GetMapping("/index-stock-data")
    public Map<String, List<String>> getNiftyIndexMapData() {
       return niftyIndexService.getNiftyIndexDataMap();
    }

    @GetMapping("/index-momentum-data/{indexName}/{endDate}")
    public List<TopN_MomentumStock> getNiftyIndexMomentumData(@PathVariable String indexName, @PathVariable Date endDate) {
        return niftyIndexMomentumService.getNiftyIndexMomentumStocks(indexName,endDate);
    }
}
