package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.time.LocalDate;

/** {@code symbol} and {@code date} are null for dataset-level issues (e.g. missing/duplicate calendar dates). */
public record BreadthDataQualityIssue(String symbol, IssueType issueType, LocalDate date, String detail) {
    public enum IssueType {
        MISSING_DATE,
        DUPLICATE_DATE,
        INVALID_OHLCV,
        INVALID_VOLUME,
        INSUFFICIENT_HISTORY,
        STALE_INSTRUMENT
    }
}
