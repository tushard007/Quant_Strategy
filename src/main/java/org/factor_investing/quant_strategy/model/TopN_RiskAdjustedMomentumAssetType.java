package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Table(name = "t_top_risk_adjusted_momentum_asset", uniqueConstraints = {
@UniqueConstraint(columnNames = {"stock_name", "strategy_run_date"})
})
@Getter
@Setter
public class TopN_RiskAdjustedMomentumAssetType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;
    public String stockName;
    public float percentageReturn12Months;
    public float percentageReturn6Months;
    public float percentageReturn3Months;
    public float volatility;
    public float inverseVolWeight;
    @Column(name = "strategy_run_date")
    public Date strategyRunDate;
    public int rank12Months;
    public int rank6Months;
    public int rank3Months;
    public int totalRankScore;
    @Enumerated(EnumType.STRING)
    public AssetDataType assetDataType;
    @CreationTimestamp
    private java.util.Date creationDate;
    @UpdateTimestamp
    private java.util.Date modificationDate;
}
