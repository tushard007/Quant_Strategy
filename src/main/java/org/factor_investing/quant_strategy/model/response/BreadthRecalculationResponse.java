package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;

import java.time.LocalDate;

public record BreadthRecalculationResponse(NiftyIndexName universe,
                                           BreadthMethodology methodology,
                                           LocalDate fromDate,
                                           LocalDate toDate,
                                           int calculatedCount,
                                           int failureCount) {
}
