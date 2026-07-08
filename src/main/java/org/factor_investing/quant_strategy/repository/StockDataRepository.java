package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockDataRepository extends JpaRepository<StockPricesJson, Long> {
}
