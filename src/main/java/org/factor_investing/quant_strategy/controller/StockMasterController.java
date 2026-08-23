package org.factor_investing.quant_strategy.controller;

import jakarta.validation.Valid;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.request.StockMasterRequest;
import org.factor_investing.quant_strategy.model.response.StockMasterImportResponse;
import org.factor_investing.quant_strategy.service.NSE_StockDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/stock-master")
public class StockMasterController {
    private final NSE_StockDataService stockDataService;

    public StockMasterController(NSE_StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    @GetMapping
    public List<NSEStockMasterData> findAll(@RequestParam(required = false) String search) {
        return stockDataService.searchStockData(search);
    }

    @GetMapping("/{id}")
    public NSEStockMasterData findById(@PathVariable Long id) {
        return stockDataService.requireStockDataById(id);
    }

    @PostMapping
    public ResponseEntity<NSEStockMasterData> create(@Valid @RequestBody StockMasterRequest request) {
        NSEStockMasterData created = stockDataService.createStockData(request);
        return ResponseEntity.created(URI.create("/api/stock-master/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public NSEStockMasterData update(@PathVariable Long id, @Valid @RequestBody StockMasterRequest request) {
        return stockDataService.updateStockData(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stockDataService.deleteStockData(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import-csv", consumes = "multipart/form-data")
    public StockMasterImportResponse importCsv(@RequestParam("file") MultipartFile file) {
        return stockDataService.replaceFromCsv(file);
    }
}
