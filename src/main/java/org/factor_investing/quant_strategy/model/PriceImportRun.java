package org.factor_investing.quant_strategy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "price_import_run")
@Getter
@Setter
public class PriceImportRun {
    @Id
    private String id;
    private String source;
    @Enumerated(EnumType.STRING)
    private PriceFrequencey timeFrame;
    private String status;
    @Column(columnDefinition = "text")
    private String message;
    private int processed;
    private int total;
    private int saved;
    @Column(columnDefinition = "text")
    private String failedSymbols = "";
    private Instant updatedAt;
}
