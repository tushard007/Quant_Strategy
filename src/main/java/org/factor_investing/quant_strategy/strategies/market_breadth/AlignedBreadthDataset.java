package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.OHLCV;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Per-symbol daily price series for a universe, restricted to the trading calendar derived from
 * the universe's own index price history. Never forward-filled: a symbol simply has no entry for
 * a date it has no bar for.
 */
public record AlignedBreadthDataset(NiftyIndexName universe,
                                     BreadthMethodology methodology,
                                     LocalDate calculationDate,
                                     List<LocalDate> tradingDates,
                                     List<String> members,
                                     Map<String, NavigableMap<LocalDate, OHLCV>> seriesBySymbol,
                                     List<BreadthDataQualityIssue> sourceIssues) {
}
