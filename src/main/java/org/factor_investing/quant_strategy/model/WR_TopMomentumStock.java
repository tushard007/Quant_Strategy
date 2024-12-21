package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @CreationTimestamp
    private java.util.Date creationDate;
    @UpdateTimestamp
    private java.util.Date modificationDate;

}
