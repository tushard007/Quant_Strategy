package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.AssetDataType;
import java.sql.Date;
import java.time.Instant;

public record RiskAdjustedMomentumExecutionSummary(AssetDataType assetDataType, Date strategyRunDate, long resultCount, long analyzedCount, Instant lastUpdatedAt) {
}
