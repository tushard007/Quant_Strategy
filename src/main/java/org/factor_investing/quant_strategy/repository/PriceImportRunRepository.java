package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.PriceImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceImportRunRepository extends JpaRepository<PriceImportRun, String> {}
