package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name="nse_ETF_master_data")
public class NSE_ETFMasterData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;
   // Symbol of the ETF
    private String symbol;

    // Underlying index or asset
    private String underlying;

    // Name of the ETF security
    private String securityName;

    // Date of listing on the exchange
    private String dateOfListing;

    // Market lot size for trading
    private Integer marketLot;

    // ISIN number for the ETF
    private String isinNumber;

    // Face value of the ETF
    private Double faceValue;
}
