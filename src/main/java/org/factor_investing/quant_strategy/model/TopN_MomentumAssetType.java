package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Table(name = "t_top_momentum_stock",uniqueConstraints = {
@UniqueConstraint(columnNames = {"stock_name", "strategy_run_date"})
})
@Getter
@Setter
public class TopN_MomentumAssetType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;
    public String stockName;
    public float percentageReturn12Months;
    public float percentageReturn6Months;
    public float percentageReturn3Months;
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
