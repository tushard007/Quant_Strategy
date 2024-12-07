package org.factor_investing.quant_strategy.momentum.repository;

import org.factor_investing.quant_strategy.StockPriceData;
import org.factor_investing.quant_strategy.momentum.model.TopN_MomentumStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumStock, Integer>{

}

