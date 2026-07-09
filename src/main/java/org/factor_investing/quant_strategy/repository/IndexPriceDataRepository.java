package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexPriceDataRepository extends JpaRepository<IndexPricesJson, Long> {
}
