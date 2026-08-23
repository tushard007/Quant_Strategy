package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NSE_ETFMasterDataRepository extends JpaRepository<NSE_ETFMasterData, Long> {
    Optional<NSE_ETFMasterData> findBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCaseAndIdNot(String symbol, Long id);
}
