package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "momentum_backtest_run")
@Getter
@Setter
public class MomentumBacktestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(nullable = false)
    private double initialCapital;
    @Column(nullable = false)
    private int entryRank;
    @Column(nullable = false)
    private int retentionRank;
    @Column(nullable = false)
    private String benchmark;
    @Column(nullable = false)
    private double transactionCostPercent;
    @Column(nullable = false)
    private double slippagePercent;
    @Column(nullable = false)
    private double riskFreeRatePercent;
    @Column(nullable = false)
    private String rebalanceMode;
    @Column(nullable = false)
    private double bufferAmount;
    @Column(nullable = false)
    private double maximumLeverageAmount;
    @Column(nullable = false)
    private double borrowingInterestRatePercent;
    @Column(nullable = false)
    private double finalValue;
    @Column(nullable = false)
    private double totalReturn;
    @Column(nullable = false)
    private double cagr;
    @Column(nullable = false)
    private double maximumDrawdown;
    @Column(nullable = false)
    private double sharpeRatio;
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private MomentumBacktestResult result;
}
