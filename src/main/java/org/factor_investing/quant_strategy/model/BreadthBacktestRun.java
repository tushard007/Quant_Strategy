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
import lombok.Getter;
import lombok.Setter;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunResult;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "breadth_backtest_run")
@Getter
@Setter
public class BreadthBacktestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false)
    private BreadthBacktestRunType runType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreadthBacktestStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "initial_capital", nullable = false)
    private double initialCapital;
    @Column(name = "entry_rank", nullable = false)
    private int entryRank;
    @Column(name = "retention_rank", nullable = false)
    private int retentionRank;
    @Column(nullable = false)
    private String benchmark;

    @Enumerated(EnumType.STRING)
    @Column(name = "breadth_universe", nullable = false)
    private NiftyIndexName breadthUniverse;
    @Enumerated(EnumType.STRING)
    @Column(name = "breadth_entry_mode")
    private BreadthEntryMode breadthEntryMode;
    @Column(name = "breadth_score_cutoff")
    private Double breadthScoreCutoff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreadthMethodology methodology;
    @Column(name = "score_configuration_version", nullable = false)
    private int scoreConfigurationVersion;

    @Column(name = "baseline_total_return")
    private Double baselineTotalReturn;
    @Column(name = "baseline_cagr")
    private Double baselineCagr;
    @Column(name = "baseline_sharpe_ratio")
    private Double baselineSharpeRatio;
    @Column(name = "baseline_maximum_drawdown")
    private Double baselineMaximumDrawdown;
    @Column(name = "filtered_total_return")
    private Double filteredTotalReturn;
    @Column(name = "filtered_cagr")
    private Double filteredCagr;
    @Column(name = "filtered_sharpe_ratio")
    private Double filteredSharpeRatio;
    @Column(name = "filtered_maximum_drawdown")
    private Double filteredMaximumDrawdown;

    @Column(name = "sample_count")
    private Integer sampleCount;
    @Column(name = "error_message")
    private String errorMessage;

    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private BreadthBacktestRunResult result;
}
