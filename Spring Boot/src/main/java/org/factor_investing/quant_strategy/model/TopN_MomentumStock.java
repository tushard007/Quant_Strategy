package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Table(name = "t_top_momentum_stock")
@Getter
@Setter
public class TopN_MomentumStock{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;
    public String stockName;
    @Enumerated(EnumType.STRING)
    public RebalenceStrategy rebalancedStrategy;
    public float percentageReturn12Months;
    public float percentageReturn6Months;
    public float percentageReturn3Months;
    public Date startDate;
    public Date endDate;
    public float startDateStockPrice;
    public float endDateStockPrice;
    public int rank;
    @CreationTimestamp
    private java.util.Date creationDate;
    @UpdateTimestamp
    private java.util.Date modificationDate;

    @Transient
    private java.sql.Date buyDate;
    @Transient
    private java.sql.Date sellDate;
    @Transient
    private float buyPrice;
    @Transient
    private float sellPrice;
    @Transient
    private String strategyRunningMonth;
}
