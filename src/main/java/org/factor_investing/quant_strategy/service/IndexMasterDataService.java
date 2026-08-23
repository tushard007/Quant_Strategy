package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.factor_investing.quant_strategy.model.request.IndexMasterRequest;
import org.factor_investing.quant_strategy.repository.NSEIndexMasterDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IndexMasterDataService {
    private final NSEIndexMasterDataRepository repository;
    public IndexMasterDataService(NSEIndexMasterDataRepository repository) { this.repository = repository; }

    public List<NSEIndexMasterData> search(String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(index -> term.isEmpty() || contains(index.getSymbol(), term) || contains(index.getIndexName(), term) || contains(index.getInstrumentKey(), term))
                .sorted((left, right) -> left.getSymbol().compareToIgnoreCase(right.getSymbol())).toList();
    }

    public NSEIndexMasterData requireById(Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Index master record not found: " + id)); }

    public NSEIndexMasterData create(IndexMasterRequest request) {
        validateUnique(request, null);
        return repository.save(apply(new NSEIndexMasterData(), request));
    }

    public NSEIndexMasterData update(Long id, IndexMasterRequest request) {
        NSEIndexMasterData index = requireById(id);
        validateUnique(request, id);
        return repository.save(apply(index, request));
    }

    public void delete(Long id) { repository.delete(requireById(id)); }

    private void validateUnique(IndexMasterRequest request, Long id) {
        String symbol = normalize(request.symbol());
        String instrumentKey = request.instrumentKey().trim();
        boolean duplicateSymbol = id == null ? repository.existsBySymbolIgnoreCase(symbol) : repository.existsBySymbolIgnoreCaseAndIdNot(symbol, id);
        boolean duplicateKey = id == null ? repository.existsByInstrumentKeyIgnoreCase(instrumentKey) : repository.existsByInstrumentKeyIgnoreCaseAndIdNot(instrumentKey, id);
        if (duplicateSymbol) throw new ResponseStatusException(CONFLICT, "An index with symbol " + symbol + " already exists");
        if (duplicateKey) throw new ResponseStatusException(CONFLICT, "An index with instrument key " + instrumentKey + " already exists");
    }

    private NSEIndexMasterData apply(NSEIndexMasterData index, IndexMasterRequest request) { index.setSymbol(normalize(request.symbol())); index.setIndexName(request.indexName().trim()); index.setInstrumentKey(request.instrumentKey().trim()); return index; }
    private String normalize(String value) { return value.trim().toUpperCase(); }
    private boolean contains(String value, String term) { return value != null && value.toLowerCase().contains(term); }
}
