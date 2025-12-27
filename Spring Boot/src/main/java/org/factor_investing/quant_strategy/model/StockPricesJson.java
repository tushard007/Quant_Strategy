package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
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

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<OHLCV> ohlcvData;

    @Enumerated(EnumType.STRING)
    private AssetDataType nseDataType;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "stockSymbol", referencedColumnName = "symbol",nullable = false)
    private NSEStockMasterData nseStockMasterData;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "IndexSymbol", referencedColumnName = "symbol")
    private NSE_ETFMasterData nseETFMasterData;

    @Enumerated(EnumType.STRING)
    private PriceFrequencey timeFrame;

    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
