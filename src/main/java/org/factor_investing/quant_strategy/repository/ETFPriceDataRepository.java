package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.ETFPricesJson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ETFPriceDataRepository extends JpaRepository<ETFPricesJson, Long> {
}
