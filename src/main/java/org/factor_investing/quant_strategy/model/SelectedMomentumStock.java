package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.factor_investing.quant_strategy.momentum.service.TopMomentumStockService;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Table(name = "t_selected_momentum_stock")
@Getter
@Setter
public class SelectedMomentumStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String stockName;
    private Date rebalenceDate;
    @Enumerated(EnumType.STRING)
    private RebalenceStrategy rebalenceStrategy;
    @Enumerated(EnumType.STRING)
    private StockSignal stockSignal;
    @CreationTimestamp
    private java.util.Date creationDate;
    @UpdateTimestamp
    private java.util.Date modificationDate;

}
