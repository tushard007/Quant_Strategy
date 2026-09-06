package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ETFCommoditySelectionRequest(
        @NotNull Set<@NotNull Long> scopeIds,
        @NotNull Set<@NotNull Long> selectedIds
) {}
