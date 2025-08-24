package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "t_stock_price_data_json")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class StockPricesJson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @Column(name = "stock_symbol")
//    private String stockSymbol;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<OHLCV> ohlcvData;

    @Enumerated(EnumType.STRING)
    private AssetDataType nsseDataType;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "stockSymbol", referencedColumnName = "symbol",nullable = false)
    private NSEStockMasterData nseStockMasterData;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "IndexSymbol", referencedColumnName = "symbol")
    private NSE_ETFMasterData nse_etfMasterData;
}
