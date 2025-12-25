package org.factor_investing.quant_strategy.technical_analysis;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class BarSeriesService {
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
}
