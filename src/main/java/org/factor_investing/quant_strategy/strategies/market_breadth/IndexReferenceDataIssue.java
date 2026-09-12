package org.factor_investing.quant_strategy.strategies.market_breadth;

public record IndexReferenceDataIssue(String symbol, IssueType issueType, String detail) {
    public enum IssueType {
        MISSING_INSTRUMENT,
        MISSING_INSTRUMENT_KEY,
        MISSING_HISTORY,
        STALE_HISTORY
    }
}
