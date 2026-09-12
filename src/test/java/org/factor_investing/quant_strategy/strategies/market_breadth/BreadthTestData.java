package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.strategies.OHLCV;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.NavigableMap;
import java.util.TreeMap;

final class BreadthTestData {
    static OHLCV bar(LocalDate date, double close, long volume) {
        return new OHLCV(Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)), close, close, close, close, volume);
    }

    static NavigableMap<LocalDate, OHLCV> risingSeries(LocalDate start, int count, double initial, double step) {
        NavigableMap<LocalDate, OHLCV> series = new TreeMap<>();
        for (int index = 0; index < count; index++) {
            LocalDate date = start.plusDays(index);
            series.put(date, bar(date, initial + index * step, 1000 + index));
        }
        return series;
    }

    private BreadthTestData() {
    }
}
