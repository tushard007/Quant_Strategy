package org.factor_investing.quant_strategy.technical_analysis;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class BarSeriesService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Build a BarSeries from OHLCV list.
     *
     * @param rows ordered by time ascending
     */

    public BarSeries buildSeriesFromStockPrice(List<OHLCV> rows) {
        BarSeries series = new BaseBarSeriesBuilder().withName("OHLCV-series").build();

        Duration barDuration = Duration.ofDays(1);

        // Work on a copy and ensure rows are ordered by date ascending
        List<OHLCV> sortedDate = new java.util.ArrayList<>(rows);
        sortedDate.sort(Comparator.comparing(OHLCV::getDate));
        for (OHLCV r : sortedDate) {
            if (r == null || r.getDate() == null) {
                continue;
            }
            Instant endInstant = r.getDate().toInstant();
            Instant startInstant = endInstant.minus(barDuration);

            // Skip bars that are not strictly after the last bar's end time
            if (!series.isEmpty()) {
                Instant lastEnd = series.getLastBar().getEndTime();
                if (!endInstant.isAfter(lastEnd)) {
                    continue;
                }
            }

            Bar bar = new BaseBar(barDuration, startInstant, endInstant, DecimalNum.valueOf(r.getOpen()), DecimalNum.valueOf(r.getHigh()), DecimalNum.valueOf(r.getLow()), DecimalNum.valueOf(r.getClose()), DecimalNum.valueOf(r.getVolume()), DecimalNum.valueOf(0), rows.size());

            series.addBar(bar);
        }
        return series;
    }

    public BarSeries buildWeeklySeriesFromStockPrice(List<OHLCV> rows) {
        BarSeries series = new BaseBarSeriesBuilder().withName("OHLCV-weekly-series").build();

        if (rows == null || rows.isEmpty()) {
            return series;
        }

        List<OHLCV> sortedRows = new ArrayList<>(rows);
        sortedRows.sort(Comparator.comparing(OHLCV::getDate));

        Map<LocalDate, List<OHLCV>> weeklyRows = new TreeMap<>();
        for (OHLCV row : sortedRows) {
            if (row == null || row.getDate() == null) {
                continue;
            }

            LocalDate candleDate = row.getDate().toInstant().atZone(MARKET_ZONE).toLocalDate();
            LocalDate weekStart = candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyRows.computeIfAbsent(weekStart, ignored -> new ArrayList<>()).add(row);
        }

        weeklyRows.forEach((weekStart, weekRows) -> {
            if (weekRows.isEmpty()) {
                return;
            }

            OHLCV first = weekRows.get(0);
            OHLCV last = weekRows.get(weekRows.size() - 1);
            double high = weekRows.stream().mapToDouble(OHLCV::getHigh).max().orElse(last.getHigh());
            double low = weekRows.stream().mapToDouble(OHLCV::getLow).min().orElse(last.getLow());
            long volume = weekRows.stream().mapToLong(OHLCV::getVolume).sum();

            Instant startInstant = weekStart.atStartOfDay(MARKET_ZONE).toInstant();
            Instant endInstant = weekStart.plusWeeks(1).atStartOfDay(MARKET_ZONE).toInstant();

            Bar bar = new BaseBar(Duration.ofDays(7), startInstant, endInstant,
                    DecimalNum.valueOf(first.getOpen()),
                    DecimalNum.valueOf(high),
                    DecimalNum.valueOf(low),
                    DecimalNum.valueOf(last.getClose()),
                    DecimalNum.valueOf(volume),
                    DecimalNum.valueOf(0),
                    weeklyRows.size());

            series.addBar(bar);
        });

        return series;
    }
}
