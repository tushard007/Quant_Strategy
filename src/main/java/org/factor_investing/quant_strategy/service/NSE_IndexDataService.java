package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.repository.NSE_ETFMasterDataRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NSE_IndexDataService {
    private final NSE_ETFMasterDataRepository indexRepository;

    public NSE_IndexDataService(NSE_ETFMasterDataRepository indexRepository) {
        this.indexRepository = indexRepository;
    }

    public void saveIndexData(NSE_ETFMasterData indexData) {
        indexRepository.save(indexData);
    }

    public NSE_ETFMasterData getIndexDataById(Long id) {
        return indexRepository.findById(id).orElse(null);
    }

    public void deleteIndexData(Long id) {
        indexRepository.deleteById(id);
    }

    public List<NSE_ETFMasterData> getAllIndexData() {
        return indexRepository.findAll();
    }

    public void updateIndexData(NSE_ETFMasterData indexData) {
        if (indexData == null || indexData.getId() == null) {
            throw new IllegalArgumentException("Index data or ID must not be null.");
        }

        if (indexRepository.existsById(indexData.getId())) {
            indexRepository.save(indexData);
        } else {
            throw new IllegalArgumentException(
                    "Index data with ID " + indexData.getId() + " does not exist."
            );
        }
    }

    public NSE_ETFMasterData getIndexDataBySymbol(String symbol) {
        if (symbol == null) return null;
        return indexRepository.findAll().stream()
                .filter(index -> symbol.equalsIgnoreCase(index.getSymbol()))
                .findFirst()
                .orElse(null);
    }

    public void deleteIndexDataBySymbol(String symbol) {
        NSE_ETFMasterData indexData = getIndexDataBySymbol(symbol);
        if (indexData != null) {
            indexRepository.delete(indexData);
        } else {
            throw new IllegalArgumentException(
                    "Index data with symbol " + symbol + " does not exist."
            );
        }
    }

    public void saveAllIndexData(Iterable<NSE_ETFMasterData> indexDataList) {
        indexRepository.saveAll(indexDataList);
    }
}
