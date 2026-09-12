package org.factor_investing.quant_strategy.model.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;

import java.time.LocalDate;

public record BreadthRecalculationRequest(@NotNull LocalDate fromDate,
                                          LocalDate toDate,
                                          @NotNull NiftyIndexName universe,
                                          @NotNull BreadthMethodology methodology) {
    public LocalDate effectiveToDate() {
        return toDate == null ? fromDate : toDate;
    }

    @AssertTrue(message = "toDate must be on or after fromDate")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !toDate.isBefore(fromDate);
    }

    @AssertTrue(message = "date range cannot exceed 10 years")
    public boolean isDateRangeWithinLimit() {
        return fromDate == null || toDate == null || !toDate.isAfter(fromDate.plusYears(10));
    }
}
