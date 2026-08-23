package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NSEStockMasterDataRepository extends JpaRepository<NSEStockMasterData, Long> {
    Optional<NSEStockMasterData> findBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCaseAndIdNot(String symbol, Long id);
}
