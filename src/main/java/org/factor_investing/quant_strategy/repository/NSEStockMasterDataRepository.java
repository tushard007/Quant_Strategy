package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NSEStockMasterDataRepository extends JpaRepository<NSEStockMasterData, Long> {


}