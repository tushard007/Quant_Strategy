package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.sql.Date;

@Entity

@Getter
@Setter
@Table(name="stock_prices")
public class StockPriceData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

   @Column(name = "symbol")
   private String symbol;

   @Column(name = "date")
   private Date date;

   @Column(name = "open")
   private double open;

   @Column(name = "high")
   private double high;

   @Column(name = "low")
   private double low;

   @Column(name = "close")
   private double close;

   @Column(name = "volume")
   private long volume;

    @Column(name = "created_at", columnDefinition = "priceDate default CURRENT_TIMESTAMP")
    private java.sql.Timestamp createdAt;
}
