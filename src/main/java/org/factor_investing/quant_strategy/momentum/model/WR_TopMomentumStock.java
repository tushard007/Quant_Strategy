package org.factor_investing.quant_strategy.momentum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "t_wr_top_momentum_stock")
@Getter
@Setter
public class WR_TopMomentumStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String stockName;
    private Date rebalenceDate;
    private Float percentageReturn;
}
