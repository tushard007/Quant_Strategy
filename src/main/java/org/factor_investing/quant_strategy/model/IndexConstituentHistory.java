package org.factor_investing.quant_strategy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "index_constituent_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_index_constituent_history", columnNames = {"index_name", "stock_symbol", "effective_from"}))
@Getter
@Setter
public class IndexConstituentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_name", nullable = false)
    private NiftyIndexName indexName;

    @Column(name = "stock_symbol", nullable = false)
    private String stockSymbol;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means the stock is still a current member as of the latest known data. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private String source;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;
}
