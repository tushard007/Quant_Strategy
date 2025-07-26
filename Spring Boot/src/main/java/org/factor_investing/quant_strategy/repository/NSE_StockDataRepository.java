package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.NSE_StockMasterData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NSE_StockDataRepository extends JpaRepository<NSE_StockMasterData, Long> {
}
