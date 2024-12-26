package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.factor_investing.quant_strategy.momentum.service.TopMomentumStockService;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Table(name = "t_momentum_stock return")
@Getter
@Setter
public class SelectedMomentumStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String stockName;
    private String strategyRunningMonth;
    @Enumerated(EnumType.STRING)
    private RebalenceStrategy rebalenceStrategy;
    private Date buyDate;
    private Date sellDate;
    private float buyPrice;
    private float sellPrice;
    private int stockQuantity;
    private float investmentAmount;
    private float sellAmount;
    private float profitLoss;
    private float percentageReturn;
    @CreationTimestamp
    private java.util.Date creationDate;
    @UpdateTimestamp
    private java.util.Date modificationDate;

}
