package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.service.CSVWriterService;
import org.factor_investing.quant_strategy.service.NiftyIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nifty-index")
public class NiftyIndexController {
    @Autowired
    private NiftyIndexService niftyIndexService;

    @Autowired
    private CSVWriterService csvWriterService;

    @GetMapping("/index-stock-data")
    public Map<String, List<String>> getNiftyIndexMapData() {
        return niftyIndexService.getNiftyIndexDataMap();
    }



}
