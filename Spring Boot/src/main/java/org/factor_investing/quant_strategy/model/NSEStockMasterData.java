package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="t_nse_stock_master_data")
public class NSEStockMasterData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;
    @Column(name = "symbol",  unique = true)
    private String symbol;
    private String nameOfCompany;
    private String series;
    private String isinNumber;


}
