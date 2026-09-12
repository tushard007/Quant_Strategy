package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.function.ToDoubleFunction;

import static org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMetricKeys.*;

/** Calculates the complete daily breadth series and all recursive breadth signals. */
@Service
public class MarketBreadthCalculationService {
    private static final int DIVERGENCE_SHORT_LOOKBACK = 20;
    private static final int DIVERGENCE_LONG_LOOKBACK = 50;
    private static final double NEAR_HIGH_DISTANCE_PERCENT = 2;

    private final BreadthDailyStatisticsService dailyStatisticsService;
    private final BreadthCoverageService coverageService;
    private final BreadthDataQualityService qualityService;
    private final BreadthScoreService scoreService;

    public MarketBreadthCalculationService(BreadthDailyStatisticsService dailyStatisticsService,
                                            BreadthCoverageService coverageService,
                                            BreadthDataQualityService qualityService,
                                            BreadthScoreService scoreService) {
        this.dailyStatisticsService = dailyStatisticsService;
        this.coverageService = coverageService;
        this.qualityService = qualityService;
        this.scoreService = scoreService;
    }

    public List<BreadthCalculationResult> calculate(List<AlignedBreadthDataset> datasets,
                                                     BreadthIndexSeries indices,
                                                     BreadthScoreParameters parameters,
                                                     BreadthCalculationSeed seed) {
        List<BreadthDailyStatistics> rawSeries = datasets.stream().map(dailyStatisticsService::calculate).toList();
        List<BreadthCalculationResult> results = new ArrayList<>();
        double adLine = seed.adLine();
        double ema19 = seed.mcClellanEma19();
        double ema39 = seed.mcClellanEma39();
        double summation = seed.summationIndex();

        for (int index = 0; index < datasets.size(); index++) {
            AlignedBreadthDataset dataset = datasets.get(index);
            BreadthDailyStatistics raw = rawSeries.get(index);
            Map<String, Double> metrics = new LinkedHashMap<>();
            putRaw(metrics, raw);

            adLine += raw.netAdvances();
            boolean firstWithoutSeed = index == 0 && !seed.present();
            ema19 = firstWithoutSeed ? raw.netAdvances() : recursiveEma(raw.netAdvances(), ema19, 19);
            ema39 = firstWithoutSeed ? raw.netAdvances() : recursiveEma(raw.netAdvances(), ema39, 39);
            double oscillator = ema19 - ema39;
            summation += oscillator;
            metrics.put(AD_LINE, adLine);
            metrics.put(AD_RATIO_5D, average(rawSeries, index, 5, BreadthDailyStatistics::adRatio));
            metrics.put(AD_RATIO_10D, average(rawSeries, index, 10, BreadthDailyStatistics::adRatio));
            metrics.put(MCCLELLAN_EMA_19, ema19);
            metrics.put(MCCLELLAN_EMA_39, ema39);
            metrics.put(MCCLELLAN_OSCILLATOR, oscillator);
            metrics.put(MCCLELLAN_SUMMATION, summation);

            double priorOscillator = previousValue(results, MCCLELLAN_OSCILLATOR);
            metrics.put(MCCLELLAN_ZERO_CROSS, index > 0 && crossedZero(priorOscillator, oscillator) ? 1.0 : 0.0);
            metrics.put(AD_LINE_RISING, trend(results, AD_LINE, adLine, parameters.trendLookback()));
            double breadth50Trend = signedTrend(results, BREADTH_50D_PERCENT, raw.breadth50Percent(),
                    parameters.trendLookback());
            double breadth200Trend = signedTrend(results, BREADTH_200D_PERCENT, raw.breadth200Percent(),
                    parameters.trendLookback());
            metrics.put(BREADTH_50D_TREND, breadth50Trend);
            metrics.put(BREADTH_200D_TREND, breadth200Trend);
            metrics.put(BREADTH_50D_RISING, breadth50Trend > 0 ? 1.0 : 0.0);
            metrics.put(BREADTH_200D_RISING, breadth200Trend > 0 ? 1.0 : 0.0);

            double summationTrend = signedTrend(results, MCCLELLAN_SUMMATION, summation, parameters.trendLookback());
            double priorSummationTrend = previousValue(results, SUMMATION_TREND);
            metrics.put(SUMMATION_TREND, summationTrend);
            metrics.put(SUMMATION_TREND_FLIP,
                    index > 0 && summationTrend != 0 && priorSummationTrend != 0
                            && Math.signum(summationTrend) != Math.signum(priorSummationTrend) ? 1.0 : 0.0);
            double netHighTrend5 = signedTrend(results, NET_NEW_HIGHS, raw.netNewHighs(), 5);
            double netHighTrend10 = signedTrend(results, NET_NEW_HIGHS, raw.netNewHighs(), 10);
            metrics.put(NET_NEW_HIGHS_TREND_5D, netHighTrend5);
            metrics.put(NET_NEW_HIGHS_TREND_10D, netHighTrend10);
            metrics.put(NET_NEW_HIGHS_EXPANDING_5D, netHighTrend5 > 0 ? 1.0 : 0.0);
            metrics.put(NET_NEW_HIGHS_EXPANDING_10D, netHighTrend10 > 0 ? 1.0 : 0.0);

            double zweigRatio = average(rawSeries, index, 10, BreadthDailyStatistics::advanceProportion);
            metrics.put(ZWEIG_RATIO_10D, zweigRatio);
            metrics.put(ZWEIG_THRUST, isZweigThrust(results, zweigRatio) ? 1.0 : 0.0);

            addSectorParticipation(metrics, indices.sectors(), dataset.calculationDate());
            addBenchmarkSignals(metrics, indices.benchmark(), dataset.calculationDate(), results);
            addVixSignals(metrics, indices.indiaVix(), dataset.calculationDate(), parameters.vixSpikePercent());

            BreadthCoverageResult coverage = coverageService.calculateCoverage(dataset);
            metrics.put(EXPECTED_COUNT, (double) coverage.expectedCount());
            metrics.put(OBSERVED_COUNT, (double) coverage.observedCount());
            metrics.put(ELIGIBLE_COUNT, (double) coverage.eligibleCount());
            BreadthScoreResult score = scoreService.score(metrics, coverage.qualityStatus(), parameters);
            List<BreadthDataQualityIssue> issues = qualityService.validate(dataset);
            List<BreadthOutlierWarning> warnings = qualityService.detectOutliers(dataset).stream()
                    .filter(warning -> warning.date().equals(dataset.calculationDate())).toList();

            results.add(new BreadthCalculationResult(dataset.universe(), dataset.methodology(),
                    dataset.calculationDate(), coverage, Map.copyOf(metrics), score, issues, warnings));
        }
        return List.copyOf(results);
    }

    private void putRaw(Map<String, Double> metrics, BreadthDailyStatistics raw) {
        metrics.put(ADVANCERS, (double) raw.advancers());
        metrics.put(DECLINERS, (double) raw.decliners());
        metrics.put(UNCHANGED, (double) raw.unchanged());
        metrics.put(ELIGIBLE_DAILY, (double) raw.eligibleDaily());
        metrics.put(NET_ADVANCES, (double) raw.netAdvances());
        metrics.put(AD_RATIO, raw.adRatio());
        metrics.put(ADVANCE_PROPORTION, raw.advanceProportion());
        metrics.put(BREADTH_50D_PERCENT, raw.breadth50Percent());
        metrics.put(BREADTH_200D_PERCENT, raw.breadth200Percent());
        metrics.put(NEW_HIGHS, (double) raw.newHighs());
        metrics.put(NEW_LOWS, (double) raw.newLows());
        metrics.put(NET_NEW_HIGHS, (double) raw.netNewHighs());
        metrics.put(NEW_HIGH_LOW_RATIO, raw.newHighLowRatio());
        metrics.put(ADVANCING_VOLUME, raw.advancingVolume());
        metrics.put(DECLINING_VOLUME, raw.decliningVolume());
        metrics.put(UP_DOWN_VOLUME_RATIO, raw.upDownVolumeRatio());
        metrics.put(TRIN, raw.trin());
        metrics.put(BULLISH_PERCENT_PROXY, raw.bullishPercentProxy());
    }

    private void addSectorParticipation(Map<String, Double> metrics,
                                        Map<RequiredMarketBreadthIndex, NavigableMap<LocalDate, OHLCV>> sectors,
                                        LocalDate date) {
        int participation = 0;
        int available = 0;
        for (RequiredMarketBreadthIndex sector : RequiredMarketBreadthIndex.sectorIndices()) {
            NavigableMap<LocalDate, OHLCV> series = sectors.get(sector);
            OHLCV current = series == null ? null : series.get(date);
            double ema50 = series == null ? Double.NaN : dailyStatisticsService.ema(series, date, 50);
            boolean above = current != null && current.getClose() > 0 && Double.isFinite(ema50)
                    && current.getClose() > ema50;
            if (current != null && Double.isFinite(ema50)) available++;
            if (above) participation++;
            metrics.put("SECTOR_" + sector.name() + "_ABOVE_50D", above ? 1.0 : 0.0);
            metrics.put("SECTOR_" + sector.name() + "_DISTANCE_PERCENT",
                    current != null && current.getClose() > 0 && Double.isFinite(ema50)
                            ? (current.getClose() / ema50 - 1) * 100 : 0);
        }
        metrics.put(SECTOR_PARTICIPATION, (double) participation);
        metrics.put(SECTOR_COUNT, (double) available);
    }

    private void addBenchmarkSignals(Map<String, Double> metrics, NavigableMap<LocalDate, OHLCV> benchmark,
                                     LocalDate date, List<BreadthCalculationResult> history) {
        OHLCV current = benchmark.get(date);
        double close = current == null ? 0 : current.getClose();
        boolean newHigh20 = isNewClosingHigh(benchmark, date, DIVERGENCE_SHORT_LOOKBACK);
        boolean newHigh50 = isNewClosingHigh(benchmark, date, DIVERGENCE_LONG_LOOKBACK);
        boolean nearHigh = isNearClosingHigh(benchmark, date, DIVERGENCE_SHORT_LOOKBACK);
        boolean adConfirm20 = confirmsHigh(history, AD_LINE, metrics.get(AD_LINE), DIVERGENCE_SHORT_LOOKBACK);
        boolean adConfirm50 = confirmsHigh(history, AD_LINE, metrics.get(AD_LINE), DIVERGENCE_LONG_LOOKBACK);
        boolean breadthConfirm = metrics.get(BREADTH_50D_RISING) > 0.5;
        boolean newHighConfirm20 = confirmsHigh(history, NET_NEW_HIGHS, metrics.get(NET_NEW_HIGHS),
                DIVERGENCE_SHORT_LOOKBACK);
        boolean newHighConfirm50 = confirmsHigh(history, NET_NEW_HIGHS, metrics.get(NET_NEW_HIGHS),
                DIVERGENCE_LONG_LOOKBACK);
        boolean bullish = (newHigh20 || newHigh50) && adConfirm20 && breadthConfirm;
        boolean bearish = (newHigh20 && (!adConfirm20 || !newHighConfirm20 || !breadthConfirm))
                || (newHigh50 && (!adConfirm50 || !newHighConfirm50 || !breadthConfirm));
        boolean narrow = nearHigh && metrics.get(SECTOR_PARTICIPATION) < 5;

        metrics.put(BENCHMARK_CLOSE, close);
        metrics.put(BENCHMARK_NEW_HIGH_20D, newHigh20 ? 1.0 : 0.0);
        metrics.put(BENCHMARK_NEW_HIGH_50D, newHigh50 ? 1.0 : 0.0);
        metrics.put(BENCHMARK_NEAR_HIGH, nearHigh ? 1.0 : 0.0);
        metrics.put(BULLISH_CONFIRMATION, bullish ? 1.0 : 0.0);
        metrics.put(BEARISH_DIVERGENCE, bearish ? 1.0 : 0.0);
        metrics.put(NARROW_LEADERSHIP, narrow ? 1.0 : 0.0);
    }

    private void addVixSignals(Map<String, Double> metrics, NavigableMap<LocalDate, OHLCV> vix,
                               LocalDate date, double spikeThresholdPercent) {
        OHLCV current = vix.get(date);
        Map.Entry<LocalDate, OHLCV> previous = vix.lowerEntry(date);
        double close = current == null ? 0 : current.getClose();
        double ema20 = dailyStatisticsService.ema(vix, date, 20);
        double change = current != null && previous != null && previous.getValue().getClose() > 0
                ? (close / previous.getValue().getClose() - 1) * 100 : 0;
        metrics.put(INDIA_VIX_CLOSE, close);
        metrics.put(INDIA_VIX_EMA_20, Double.isFinite(ema20) ? ema20 : 0);
        metrics.put(INDIA_VIX_CHANGE_PERCENT, change);
        metrics.put(INDIA_VIX_ABOVE_EMA,
                current != null && Double.isFinite(ema20) && close > ema20 ? 1.0 : 0.0);
        metrics.put(INDIA_VIX_SPIKE, change > spikeThresholdPercent ? 1.0 : 0.0);
    }

    private double recursiveEma(double current, double previous, int period) {
        double alpha = 2.0 / (period + 1);
        return current * alpha + previous * (1 - alpha);
    }

    private double average(List<BreadthDailyStatistics> values, int endIndex, int period,
                           ToDoubleFunction<BreadthDailyStatistics> extractor) {
        int from = Math.max(0, endIndex - period + 1);
        return values.subList(from, endIndex + 1).stream().mapToDouble(extractor).average().orElse(0);
    }

    private double trend(List<BreadthCalculationResult> history, String key, double current, int lookback) {
        return signedTrend(history, key, current, lookback) > 0 ? 1 : 0;
    }

    private double signedTrend(List<BreadthCalculationResult> history, String key, double current, int lookback) {
        if (history.size() < lookback) return 0;
        double prior = history.get(history.size() - lookback).indicatorValues().getOrDefault(key, current);
        return Double.compare(current, prior);
    }

    private boolean crossedZero(double previous, double current) {
        return (previous <= 0 && current > 0) || (previous >= 0 && current < 0);
    }

    private double previousValue(List<BreadthCalculationResult> history, String key) {
        return history.isEmpty() ? 0 : history.getLast().indicatorValues().getOrDefault(key, 0.0);
    }

    private boolean isZweigThrust(List<BreadthCalculationResult> history, double currentRatio) {
        if (currentRatio <= 0.615 || history.isEmpty()
                || previousValue(history, ZWEIG_RATIO_10D) > 0.615) return false;
        return history.subList(Math.max(0, history.size() - 10), history.size()).stream()
                .anyMatch(result -> result.indicatorValues().getOrDefault(ZWEIG_RATIO_10D, 1.0) < 0.40);
    }

    private boolean isNewClosingHigh(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int lookback) {
        OHLCV current = series.get(date);
        if (current == null) return false;
        List<OHLCV> window = tail(series, date, lookback);
        return window.size() == lookback
                && current.getClose() >= window.stream().mapToDouble(OHLCV::getClose).max().orElse(Double.MAX_VALUE);
    }

    private boolean isNearClosingHigh(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int lookback) {
        OHLCV current = series.get(date);
        if (current == null) return false;
        List<OHLCV> window = tail(series, date, lookback);
        double maximum = window.stream().mapToDouble(OHLCV::getClose).max().orElse(0);
        return window.size() == lookback && maximum > 0
                && (maximum - current.getClose()) / maximum * 100 <= NEAR_HIGH_DISTANCE_PERCENT;
    }

    private List<OHLCV> tail(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int lookback) {
        List<OHLCV> values = new ArrayList<>(series.headMap(date, true).values());
        return values.subList(Math.max(0, values.size() - lookback), values.size());
    }

    private boolean confirmsHigh(List<BreadthCalculationResult> history, String key, double current, int lookback) {
        List<BreadthCalculationResult> window = history.subList(Math.max(0, history.size() - lookback + 1), history.size());
        double previousMaximum = window.stream().mapToDouble(result ->
                result.indicatorValues().getOrDefault(key, Double.NEGATIVE_INFINITY)).max()
                .orElse(Double.NEGATIVE_INFINITY);
        return history.size() >= lookback - 1 && current >= previousMaximum;
    }
}
