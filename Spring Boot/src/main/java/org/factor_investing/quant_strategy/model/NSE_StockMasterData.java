package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Date;

@Entity
@Getter
@Setter
@Table(name="nse_stock_master_data")
public class NSE_StockMasterData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;
    private String symbol;
    private String nameOfCompany;
    private String series;
    private Date dateOfListing;
    private String isinNumber;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "priceDate default CURRENT_TIMESTAMP")
    private java.sql.Timestamp updatedAt;
}
