package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumStock, Integer> {


}