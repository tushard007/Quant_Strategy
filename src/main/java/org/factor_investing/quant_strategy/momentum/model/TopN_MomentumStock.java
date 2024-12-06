package org.factor_investing.quant_strategy.momentum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "t_top_momentum_stock")
@Getter
@Setter
public class TopN_MomentumStock{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long id;
    public String stockName;
    @Enumerated(EnumType.STRING)
    public RebalenceStrategy rebalancedStrategy;
    public float percentageReturn;
    public int rank;
    @Enumerated(EnumType.STRING)
    public StockSignal stockSignal;


}
