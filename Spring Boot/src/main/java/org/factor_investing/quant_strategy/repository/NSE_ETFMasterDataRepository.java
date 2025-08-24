package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NSE_ETFMasterDataRepository extends JpaRepository<NSE_ETFMasterData, Long> {
}
