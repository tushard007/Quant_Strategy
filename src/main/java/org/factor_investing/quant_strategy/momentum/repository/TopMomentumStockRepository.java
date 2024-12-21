package org.factor_investing.quant_strategy.momentum.repository;

import org.factor_investing.quant_strategy.StockPriceData;
import org.factor_investing.quant_strategy.momentum.model.RebalenceStrategy;
import org.factor_investing.quant_strategy.momentum.model.TopN_MomentumStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumStock, Integer> {
    //    @Query("SELECT tms FROM TopN_MomentumStock tms WHERE tms.rebalancedStrategy = :rebalancedStrategy " +
//                  "and tms.rank Between 1 and 20 order by tms.endDate")
    @Query(value = "select * from t_top_momentum_stock where " +
            "rebalanced_strategy=:rebalancedStrategy and rank between 1 and 20 order by end_date, rank asc",
            nativeQuery = true)
    List<TopN_MomentumStock> findAllByRebalancedStrategy(@Param("rebalancedStrategy") String rebalancedStrategy);

}