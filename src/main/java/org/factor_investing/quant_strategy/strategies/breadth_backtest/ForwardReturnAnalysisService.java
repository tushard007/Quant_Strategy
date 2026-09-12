package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.ForwardReturnAnalysisResult;
import org.factor_investing.quant_strategy.model.response.ForwardReturnObservation;
import org.factor_investing.quant_strategy.model.response.ForwardReturnStatistics;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.RiskAdjustedMomentumRiskOverlayUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Ad-hoc, non-persisted analysis of how forward NIFTY returns have historically distributed across breadth
 * regimes and score bands (BRD-090/091). Descriptive research only, not a tradable signal: forward returns
 * are close-to-close from the snapshot date itself, unlike the momentum backtest's next-open execution convention.
 */
@Service
public class ForwardReturnAnalysisService {
    private static final int SHORT_HORIZON_SESSIONS = 20;
    private static final int LONG_HORIZON_SESSIONS = 60;

    private final BreadthDailySnapshotRepository snapshotRepository;
    private final StockPriceCacheService cacheService;

    public ForwardReturnAnalysisService(BreadthDailySnapshotRepository snapshotRepository, StockPriceCacheService cacheService) {
        this.snapshotRepository = snapshotRepository;
        this.cacheService = cacheService;
    }

    public ForwardReturnAnalysisResult analyze(NiftyIndexName universe, LocalDate from, LocalDate to, String benchmarkIndex) {
        if (universe == null) throw new IllegalArgumentException("Breadth universe is required");
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("From date must be before to date");

        Map<String, NavigableMap<LocalDate, OHLCV>> indexes = RiskAdjustedMomentumRiskOverlayUtil.normalize(cacheService.getCachedAllIndexPriceData());
        NavigableMap<LocalDate, OHLCV> benchmarkSeries = RiskAdjustedMomentumRiskOverlayUtil.resolveBenchmark(indexes, benchmarkIndex);

        List<BreadthDailySnapshot> snapshots = snapshotRepository.findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(universe, from, to);

        List<ForwardReturnObservation> observations = new ArrayList<>();
        int missing20 = 0, missing60 = 0;
        for (BreadthDailySnapshot snapshot : snapshots) {
            Double[] forwardReturns = forwardReturns(benchmarkSeries, snapshot.getTradingDate());
            if (forwardReturns[0] == null) missing20++;
            if (forwardReturns[1] == null) missing60++;
            observations.add(new ForwardReturnObservation(snapshot.getTradingDate(), snapshot.getRegime(),
                    snapshot.getFinalScore(), scoreBucket(snapshot.getFinalScore()), forwardReturns[0], forwardReturns[1]));
        }

        Map<String, List<ForwardReturnObservation>> byRegimeGroups = new LinkedHashMap<>();
        Map<String, List<ForwardReturnObservation>> byBucketGroups = new LinkedHashMap<>();
        Map<String, List<ForwardReturnObservation>> byRegimeAndBucketGroups = new LinkedHashMap<>();
        for (ForwardReturnObservation observation : observations) {
            byRegimeGroups.computeIfAbsent(observation.regime().name(), key -> new ArrayList<>()).add(observation);
            String bucketKey = observation.scoreBucket() + "-" + (observation.scoreBucket() + 10);
            byBucketGroups.computeIfAbsent(bucketKey, key -> new ArrayList<>()).add(observation);
            byRegimeAndBucketGroups.computeIfAbsent(observation.regime().name() + "_" + bucketKey, key -> new ArrayList<>()).add(observation);
        }

        return new ForwardReturnAnalysisResult(universe, from, to, benchmarkIndex, observations.size(), missing20, missing60,
                statistics(byRegimeGroups), statistics(byBucketGroups), statistics(byRegimeAndBucketGroups));
    }

    /** {@code score == 100} is clamped into bucket 90 so every score maps to one of ten fixed 10-point bands. */
    static int scoreBucket(double score) {
        int bucket = (int) Math.floor(score / 10.0) * 10;
        return Math.min(90, Math.max(0, bucket));
    }

    private Double[] forwardReturns(NavigableMap<LocalDate, OHLCV> series, LocalDate tradingDate) {
        Map.Entry<LocalDate, OHLCV> base = series.floorEntry(tradingDate);
        if (base == null) return new Double[]{null, null};
        double baseClose = base.getValue().getClose();
        return new Double[]{
                closeAfterSessions(series, base.getKey(), SHORT_HORIZON_SESSIONS, baseClose),
                closeAfterSessions(series, base.getKey(), LONG_HORIZON_SESSIONS, baseClose)
        };
    }

    private Double closeAfterSessions(NavigableMap<LocalDate, OHLCV> series, LocalDate baseDate, int sessions, double baseClose) {
        LocalDate current = baseDate;
        for (int i = 0; i < sessions; i++) {
            current = series.higherKey(current);
            if (current == null) return null;
        }
        if (baseClose <= 0) return null;
        return series.get(current).getClose() / baseClose - 1;
    }

    private List<ForwardReturnStatistics> statistics(Map<String, List<ForwardReturnObservation>> groups) {
        List<ForwardReturnStatistics> rows = new ArrayList<>();
        groups.forEach((key, observations) -> {
            List<Double> returns20 = observations.stream().map(ForwardReturnObservation::forwardReturn20Session).filter(java.util.Objects::nonNull).toList();
            List<Double> returns60 = observations.stream().map(ForwardReturnObservation::forwardReturn60Session).filter(java.util.Objects::nonNull).toList();
            rows.add(new ForwardReturnStatistics(key, observations.size(),
                    mean(returns20), median(returns20), positiveRatePercent(returns20), volatility(returns20), worst(returns20),
                    mean(returns60), median(returns60), positiveRatePercent(returns60), volatility(returns60), worst(returns60)));
        });
        return rows.stream().sorted(Comparator.comparing(ForwardReturnStatistics::groupKey)).toList();
    }

    private double mean(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size(), mid = size / 2;
        return size % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }

    private double positiveRatePercent(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().filter(value -> value > 0).count() * 100.0 / values.size();
    }

    private double volatility(List<Double> values) {
        if (values.size() < 2) return 0;
        double mean = mean(values);
        double variance = values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    private double worst(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }
}
