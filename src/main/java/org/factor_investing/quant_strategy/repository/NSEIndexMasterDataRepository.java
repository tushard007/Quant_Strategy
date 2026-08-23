package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NSEIndexMasterDataRepository extends JpaRepository<NSEIndexMasterData, Long> {

    Optional<NSEIndexMasterData> findBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCaseAndIdNot(String symbol, Long id);
    boolean existsByInstrumentKeyIgnoreCase(String instrumentKey);
    boolean existsByInstrumentKeyIgnoreCaseAndIdNot(String instrumentKey, Long id);
}
