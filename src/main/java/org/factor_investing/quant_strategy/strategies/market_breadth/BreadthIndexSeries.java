package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.strategies.OHLCV;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;

public record BreadthIndexSeries(NavigableMap<LocalDate, OHLCV> benchmark,
                                 Map<RequiredMarketBreadthIndex, NavigableMap<LocalDate, OHLCV>> sectors,
                                 NavigableMap<LocalDate, OHLCV> indiaVix) {
}
