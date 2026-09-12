package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.NiftyIndexStockDetail;
import org.factor_investing.quant_strategy.model.response.NiftyIndexStockImportResponse;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/nifty-index-stock")
public class NiftyIndexStockController {
    private final NiftyIndexStockService niftyIndexStockService;

    public NiftyIndexStockController(NiftyIndexStockService niftyIndexStockService) {
        this.niftyIndexStockService = niftyIndexStockService;
    }

    @GetMapping("/{indexName}")
    public List<NiftyIndexStockDetail> listForIndex(@PathVariable NiftyIndexName indexName) {
        return niftyIndexStockService.listForIndex(indexName);
    }

    @PostMapping("/{indexName}/bulk")
    public NiftyIndexStockImportResponse replaceFromBulkList(@PathVariable NiftyIndexName indexName, @RequestBody List<String> symbols) {
        return niftyIndexStockService.replaceIndexColumn(indexName, symbols);
    }

    @PostMapping(value = "/{indexName}/import", consumes = "multipart/form-data")
    public NiftyIndexStockImportResponse replaceFromCsv(@PathVariable NiftyIndexName indexName, @RequestParam("file") MultipartFile file) {
        return niftyIndexStockService.replaceIndexColumnFromCsv(indexName, file);
    }
}
