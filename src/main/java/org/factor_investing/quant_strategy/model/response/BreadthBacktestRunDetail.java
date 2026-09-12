package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.BreadthBacktestRunType;
import org.factor_investing.quant_strategy.model.BreadthBacktestStatus;

import java.time.Instant;
import java.util.UUID;

/** Exactly one of {@code backtestResult}/{@code sensitivityResult} is populated, per {@code runType}. */
public record BreadthBacktestRunDetail(
        UUID id, Instant createdAt, BreadthBacktestRunType runType, BreadthBacktestStatus status,
        BreadthFilteredMomentumBacktestResult backtestResult, BreadthThresholdSensitivityResult sensitivityResult) {
}
