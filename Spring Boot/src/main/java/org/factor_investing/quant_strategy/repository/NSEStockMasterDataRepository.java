package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NSEStockMasterDataRepository extends JpaRepository<NSEStockMasterData, Long> {


}