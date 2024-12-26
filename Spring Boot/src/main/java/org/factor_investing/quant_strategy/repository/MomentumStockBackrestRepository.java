package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.SelectedMomentumStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MomentumStockBackrestRepository extends JpaRepository<SelectedMomentumStock,Integer> {
}
