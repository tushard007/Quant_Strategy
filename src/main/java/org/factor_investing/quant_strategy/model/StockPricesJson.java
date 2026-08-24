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
@Table(name = "t_stock_price_data_json", uniqueConstraints = @UniqueConstraint(name = "uk_stock_price_symbol_timeframe", columnNames = {"stock_symbol", "time_frame"}))
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

    @ManyToOne
    @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol", nullable = false)
    private NSEStockMasterData nseStockMasterData;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_frame", nullable = false)
    private PriceFrequencey timeFrame;

    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
