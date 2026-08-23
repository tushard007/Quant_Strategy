package org.factor_investing.quant_strategy.controller;

import jakarta.validation.Valid;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.request.ETFMasterRequest;
import org.factor_investing.quant_strategy.model.response.ETFMasterImportResponse;
import org.factor_investing.quant_strategy.service.ETFMasterDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/etf-master")
public class ETFMasterController {
    private final ETFMasterDataService service;
    public ETFMasterController(ETFMasterDataService service) { this.service = service; }
    @GetMapping public List<NSE_ETFMasterData> findAll(@RequestParam(required = false) String search) { return service.search(search); }
    @GetMapping("/{id}") public NSE_ETFMasterData findById(@PathVariable Long id) { return service.requireById(id); }
    @PostMapping public ResponseEntity<NSE_ETFMasterData> create(@Valid @RequestBody ETFMasterRequest request) { NSE_ETFMasterData created = service.create(request); return ResponseEntity.created(URI.create("/api/etf-master/" + created.getId())).body(created); }
    @PutMapping("/{id}") public NSE_ETFMasterData update(@PathVariable Long id, @Valid @RequestBody ETFMasterRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @PostMapping(value = "/import-csv", consumes = "multipart/form-data") public ETFMasterImportResponse importCsv(@RequestParam("file") MultipartFile file) { return service.replaceFromCsv(file); }
}
