package org.factor_investing.quant_strategy;

import jakarta.persistence.*;


import java.sql.Date;

@Entity
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

    public float getStockPrice() {
        return stockPrice;
    }

    public void setStockPrice(float stockPrice) {
        this.stockPrice = stockPrice;
    }

    public String getStockTicker() {
        return stockTicker;
    }

    public void setStockTicker(String stockTicker) {
        this.stockTicker = stockTicker;
    }

    public Date getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(Date priceDate) {
        this.priceDate = priceDate;
    }
}
