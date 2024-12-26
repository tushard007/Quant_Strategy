package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.sql.Date;

@Entity

@Getter
@Setter
@Table(name="stock_price_data")
public class StockPriceData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "ticker")
    private String stockTicker;

    @Column(name = "closing_price")
    private float stockPrice;

    @Column(name = "date")
    private Date priceDate;

}
