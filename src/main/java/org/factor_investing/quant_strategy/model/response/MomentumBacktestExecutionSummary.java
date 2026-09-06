package org.factor_investing.quant_strategy.model.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MomentumBacktestExecutionSummary(
        UUID id, Instant createdAt, LocalDate startDate, LocalDate endDate,
        int entryRank, int retentionRank, String benchmark, String rebalanceMode,
        double finalValue, double totalReturn, double cagr,
        double maximumDrawdown, double sharpeRatio) {}
