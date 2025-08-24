package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.TopN_MomentumAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumAssetType, Integer> {


}