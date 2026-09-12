package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/** Calculates the non-recursive constituent breadth values for one trading date. */
@Service
public class BreadthDailyStatisticsService {

    public BreadthDailyStatistics calculate(AlignedBreadthDataset dataset) {
        int advancers = 0;
        int decliners = 0;
        int unchanged = 0;
        int eligibleDaily = 0;
        int above50 = 0;
        int eligible50 = 0;
        int above200 = 0;
        int eligible200 = 0;
        int newHighs = 0;
        int newLows = 0;
        int eligibleHighLow = 0;
        int bullish = 0;
        int eligibleBullish = 0;
        double advancingVolume = 0;
        double decliningVolume = 0;

        for (String symbol : dataset.members()) {
            NavigableMap<LocalDate, OHLCV> series = dataset.seriesBySymbol().get(symbol);
            if (series == null) {
                continue;
            }
            OHLCV current = series.get(dataset.calculationDate());
            Map.Entry<LocalDate, OHLCV> previousEntry = series.lowerEntry(dataset.calculationDate());
            if (!validClose(current) || previousEntry == null || !validClose(previousEntry.getValue())) {
                continue;
            }
            eligibleDaily++;
            double movePercent = (current.getClose() / previousEntry.getValue().getClose() - 1) * 100;
            boolean isAdvancer = false;
            boolean isDecliner = false;
            if (movePercent > BreadthCalculationConventions.UNCHANGED_PRICE_EPSILON_PERCENT) {
                advancers++;
                isAdvancer = true;
                advancingVolume += Math.max(0, current.getVolume());
            } else if (movePercent < -BreadthCalculationConventions.UNCHANGED_PRICE_EPSILON_PERCENT) {
                decliners++;
                isDecliner = true;
                decliningVolume += Math.max(0, current.getVolume());
            } else {
                unchanged++;
            }

            double ema50 = ema(series, dataset.calculationDate(), 50);
            if (Double.isFinite(ema50)) {
                eligible50++;
                if (current.getClose() > ema50) above50++;
            }
            double ema200 = ema(series, dataset.calculationDate(), 200);
            if (Double.isFinite(ema200)) {
                eligible200++;
                if (current.getClose() > ema200) above200++;
            }
            if (Double.isFinite(ema50) && Double.isFinite(ema200)) {
                eligibleBullish++;
                if (current.getClose() > ema50 && ema50 > ema200) bullish++;
            }

            List<OHLCV> highLowWindow = tail(series, dataset.calculationDate(),
                    BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS);
            if (highLowWindow.size() == BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS) {
                eligibleHighLow++;
                double maximumHigh = highLowWindow.stream().mapToDouble(OHLCV::getHigh).max().orElse(current.getHigh());
                double minimumLow = highLowWindow.stream().mapToDouble(OHLCV::getLow).min().orElse(current.getLow());
                // An unchanged, illiquid stock sitting at a flat 52-week range must not count as
                // both a new high and a new low every day.
                if (isAdvancer && current.getHigh() >= maximumHigh) newHighs++;
                if (isDecliner && current.getLow() <= minimumLow) newLows++;
            }
        }

        int netAdvances = advancers - decliners;
        double adRatio = safeRatio(advancers, decliners);
        double advanceProportion = safeRatio(advancers, advancers + decliners);
        double upDownVolumeRatio = safeRatio(advancingVolume, decliningVolume);
        double trin = decliningVolume > 0 && advancers + decliners > 0 && decliners > 0 && advancingVolume > 0
                ? (advancers / (double) decliners) / (advancingVolume / decliningVolume) : 0;

        return new BreadthDailyStatistics(dataset.calculationDate(), advancers, decliners, unchanged,
                eligibleDaily, netAdvances, adRatio, advanceProportion,
                percent(above50, eligible50), percent(above200, eligible200),
                newHighs, newLows, newHighs - newLows, safeRatio(newHighs, newLows),
                advancingVolume, decliningVolume, upDownVolumeRatio, trin,
                percent(bullish, eligibleBullish));
    }

    public double ema(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int period) {
        List<OHLCV> values = new ArrayList<>(series.headMap(date, true).values());
        if (values.size() < period) {
            return Double.NaN;
        }
        double ema = values.subList(0, period).stream().mapToDouble(OHLCV::getClose).average().orElse(Double.NaN);
        double multiplier = 2.0 / (period + 1);
        for (int index = period; index < values.size(); index++) {
            ema = values.get(index).getClose() * multiplier + ema * (1 - multiplier);
        }
        return ema;
    }

    private List<OHLCV> tail(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int size) {
        List<OHLCV> values = new ArrayList<>(series.headMap(date, true).values());
        return values.subList(Math.max(0, values.size() - size), values.size());
    }

    private boolean validClose(OHLCV bar) {
        return bar != null && bar.getClose() > 0;
    }

    private double safeRatio(double numerator, double denominator) {
        if (denominator > 0) return numerator / denominator;
        return numerator > 0 ? numerator : 0;
    }

    private double percent(int numerator, int denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }
}
