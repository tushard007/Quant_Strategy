package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "t_index_price_data_json")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class IndexPricesJson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<OHLCV> ohlcvData;

    @OneToOne
    @JoinColumn(name = "index_symbol", referencedColumnName = "symbol", nullable = false)
    private NSEIndexMasterData nseIndexMasterData;

    @Enumerated(EnumType.STRING)
    private PriceFrequencey timeFrame;

    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
