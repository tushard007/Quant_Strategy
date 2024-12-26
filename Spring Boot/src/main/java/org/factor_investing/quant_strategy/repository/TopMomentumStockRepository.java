package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.TopN_MomentumStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumStock, Integer> {

    @Query(value = "select * from t_top_momentum_stock where " +
            "rebalanced_strategy=:rebalancedStrategy and rank between 1 and 20 order by end_date, rank asc",
            nativeQuery = true)
    List<TopN_MomentumStock> findAllByRebalancedStrategy(@Param("rebalancedStrategy") String rebalancedStrategy);

}