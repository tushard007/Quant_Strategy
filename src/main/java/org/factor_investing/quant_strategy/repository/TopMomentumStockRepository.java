package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.TopN_MomentumAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.factor_investing.quant_strategy.model.AssetDataType;
import java.sql.Date;
import java.util.List;

@Repository
public interface TopMomentumStockRepository extends JpaRepository<TopN_MomentumAssetType, Integer> {
    List<TopN_MomentumAssetType> findByAssetDataTypeAndStrategyRunDateOrderByRank12MonthsAsc(AssetDataType assetDataType, Date strategyRunDate);
    void deleteByAssetDataTypeAndStrategyRunDate(AssetDataType assetDataType, Date strategyRunDate);
}
