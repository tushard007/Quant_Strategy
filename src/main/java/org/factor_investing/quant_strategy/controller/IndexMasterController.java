package org.factor_investing.quant_strategy.controller;

import jakarta.validation.Valid;
import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.factor_investing.quant_strategy.model.request.IndexMasterRequest;
import org.factor_investing.quant_strategy.service.IndexMasterDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/index-master")
public class IndexMasterController {
    private final IndexMasterDataService service;
    public IndexMasterController(IndexMasterDataService service) { this.service = service; }
    @GetMapping public List<NSEIndexMasterData> findAll(@RequestParam(required = false) String search) { return service.search(search); }
    @GetMapping("/{id}") public NSEIndexMasterData findById(@PathVariable Long id) { return service.requireById(id); }
    @PostMapping public ResponseEntity<NSEIndexMasterData> create(@Valid @RequestBody IndexMasterRequest request) { NSEIndexMasterData created = service.create(request); return ResponseEntity.created(URI.create("/api/index-master/" + created.getId())).body(created); }
    @PutMapping("/{id}") public NSEIndexMasterData update(@PathVariable Long id, @Valid @RequestBody IndexMasterRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
