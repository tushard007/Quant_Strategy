package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;

import java.time.LocalDate;
import java.util.*;

/** Shared stop-loss / market-regime overlay math used by both the Backtest and Analysis risk-adjusted momentum services. */
public class RiskAdjustedMomentumRiskOverlayUtil {

    private RiskAdjustedMomentumRiskOverlayUtil() {}

    public static double stopLevel(String model, double entryPrice, double peakPrice, NavigableMap<LocalDate, OHLCV> series,
                                    LocalDate date, double trailingStopPercent, int atrPeriod, double atrMultiplier) {
        if ("ATR".equals(model)) {
            double atr = atr(series, date, atrPeriod);
            return atr <= 0 ? Double.NEGATIVE_INFINITY : peakPrice - atrMultiplier * atr;
        }
        double width = trailingStopPercent;
        if ("TIERED".equals(model)) {
            double gain = peakPrice / entryPrice - 1;
            if (gain >= .6) width = Math.max(width, 25);
            else if (gain >= .3) width = Math.max(width, 20);
        }
        return peakPrice * (1 - width / 100);
    }

    public static double atr(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int period) {
        List<OHLCV> bars = new ArrayList<>(series.headMap(date, true).values());
        if (bars.size() < 2) return 0;
        int from = Math.max(1, bars.size() - period);
        double sum = 0;
        for (int i = from; i < bars.size(); i++) {
            OHLCV b = bars.get(i), p = bars.get(i - 1);
            sum += Math.max(b.getHigh() - b.getLow(), Math.max(Math.abs(b.getHigh() - p.getClose()), Math.abs(b.getLow() - p.getClose())));
        }
        return sum / (bars.size() - from);
    }

    public static boolean aboveSma(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int period) {
        List<OHLCV> bars = new ArrayList<>(series.headMap(date, true).values());
        if (bars.size() < period) return false;
        double sma = bars.subList(bars.size() - period, bars.size()).stream().mapToDouble(OHLCV::getClose).average().orElse(0);
        return bars.getLast().getClose() > sma;
    }

    public static double breadth(Map<String, NavigableMap<LocalDate, OHLCV>> stocks, LocalDate date, int period) {
        int valid = 0, above = 0;
        for (var series : stocks.values()) {
            List<OHLCV> bars = new ArrayList<>(series.headMap(date, true).values());
            if (bars.size() < period) continue;
            valid++;
            if (aboveSma(series, date, period)) above++;
        }
        return valid == 0 ? 0 : above * 100.0 / valid;
    }

    public static NavigableMap<LocalDate, OHLCV> resolveBenchmark(Map<String, NavigableMap<LocalDate, OHLCV>> indexes, String requested) {
        String normalizedRequested = normalizeName(requested);
        List<Map.Entry<String, NavigableMap<LocalDate, OHLCV>>> exact = indexes.entrySet().stream()
                .filter(entry -> normalizeName(entry.getKey()).equals(normalizedRequested)).toList();
        if (exact.size() == 1) return exact.getFirst().getValue();
        List<Map.Entry<String, NavigableMap<LocalDate, OHLCV>>> partial = indexes.entrySet().stream()
                .filter(entry -> normalizeName(entry.getKey()).contains(normalizedRequested) || normalizedRequested.contains(normalizeName(entry.getKey()))).toList();
        if (partial.size() == 1) return partial.getFirst().getValue();
        if (partial.size() > 1) throw new IllegalArgumentException("Benchmark name is ambiguous: " + requested + ". Matching symbols: " + partial.stream().map(Map.Entry::getKey).sorted().toList());
        throw new IllegalArgumentException("Benchmark index not found: " + requested);
    }

    public static String normalizeName(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); }

    public static Map<String, NavigableMap<LocalDate, OHLCV>> normalize(Map<String, List<OHLCV>> source) {
        if (source == null) return Map.of();
        Map<String, NavigableMap<LocalDate, OHLCV>> result = new HashMap<>();
        source.forEach((ticker, bars) -> {
            TreeMap<LocalDate, OHLCV> map = new TreeMap<>();
            if (bars != null) bars.stream().filter(Objects::nonNull).filter(b -> b.getDate() != null).forEach(b -> map.put(DateUtil.convertDateToLocalDate(b.getDate()), b));
            result.put(ticker, map);
        });
        return result;
    }

    public static OHLCV barAtOrBefore(NavigableMap<LocalDate, OHLCV> series, LocalDate date) {
        Map.Entry<LocalDate, OHLCV> entry = series == null ? null : series.floorEntry(date);
        return entry == null ? null : entry.getValue();
    }
}
