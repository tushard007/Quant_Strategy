package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "breadth_score_configuration")
@Getter
@Setter
public class BreadthScoreConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private int version;

    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> weights;

    @Column(name = "green_threshold", nullable = false)
    private double greenThreshold;

    @Column(name = "amber_threshold", nullable = false)
    private double amberThreshold;

    @Column(name = "vix_spike_percent", nullable = false)
    private double vixSpikePercent;

    @Column(name = "rising_falling_lookback_sessions", nullable = false)
    private int risingFallingLookbackSessions;

    @Column(name = "expansion_contraction_lookback_sessions", nullable = false)
    private int expansionContractionLookbackSessions;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
