package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "t_nifty_index_stock")
@Getter
@Setter
public class NiftyIndexStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String Nifty50;
    private String NiftyNext50;
    private String NiftyMidcap150;
    private String NiftySmallcap250;
    private String Nifty500;
    private String Nifty750;

}
