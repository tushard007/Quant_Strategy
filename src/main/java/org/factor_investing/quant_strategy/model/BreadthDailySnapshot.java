package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
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
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "breadth_daily_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_breadth_daily_snapshot", columnNames = {"universe", "trading_date"}))
@Getter
@Setter
public class BreadthDailySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiftyIndexName universe;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreadthMethodology methodology;

    @Column(name = "score_configuration_version", nullable = false)
    private int scoreConfigurationVersion;

    @Column(name = "coverage_percent", nullable = false)
    private double coveragePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private BreadthSnapshotQuality qualityStatus;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreadthRegime regime;

    @Type(JsonType.class)
    @Column(name = "indicator_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> indicatorValues;

    @Type(JsonType.class)
    @Column(name = "component_scores", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> componentScores;

    @Type(JsonType.class)
    @Column(name = "component_reasons", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> componentReasons;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
