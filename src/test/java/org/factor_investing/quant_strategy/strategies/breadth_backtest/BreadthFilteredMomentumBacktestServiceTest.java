package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthFilteredMomentumBacktestRequest;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.RiskAdjustedMomentumBacktestService;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BreadthFilteredMomentumBacktestServiceTest {

    @Test
    void greenOnlyModeBlocksNewEntriesWhileRegimeIsRed() {
        List<BreadthDailySnapshot> snapshots = new ArrayList<>();
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 700; i++) {
            snapshots.add(snapshot(NiftyIndexName.NIFTY500, start.plusDays(i), BreadthRegime.RED, 20, 1));
        }

        BreadthDailySnapshotRepository repository = fakeRepository(snapshots);
        BreadthFilteredMomentumBacktestService service = new BreadthFilteredMomentumBacktestService(
                new RiskAdjustedMomentumBacktestService(fixtureCache()), repository);

        BreadthFilteredMomentumBacktestRequest request = new BreadthFilteredMomentumBacktestRequest(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1), 1_000_000, 10, 20, "NIFTY 500",
                .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0, "ATR", 0, 14, 3, 2, 200, 20, 50,
                NiftyIndexName.NIFTY500, BreadthEntryMode.GREEN_ONLY, 0);

        BreadthFilteredMomentumBacktestResult result = service.run(request);

        assertThat(result.breadthFiltered().regimeExposureHistory()).isNotEmpty()
                .allSatisfy(point -> assertThat(point.newBuysAllowed()).isFalse());
        assertThat(result.baseline()).isNotNull();
        assertThat(result.breadthEntryMode()).isEqualTo(BreadthEntryMode.GREEN_ONLY);
        assertThat(result.methodology()).isEqualTo(BreadthMethodology.CURRENT_CONSTITUENTS);
    }

    @Test
    void neverConsultsASnapshotDatedAfterTheSignalDate() {
        List<BreadthDailySnapshot> snapshots = new ArrayList<>();
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 700; i++) {
            snapshots.add(snapshot(NiftyIndexName.NIFTY500, start.plusDays(i), BreadthRegime.GREEN, 90, 1));
        }
        // A "future" snapshot far past the backtest end date, deliberately RED/low-score: if the adapter
        // ever leaked look-ahead, this would flip newBuysAllowed to false near the end of the run.
        snapshots.add(snapshot(NiftyIndexName.NIFTY500, LocalDate.of(2030, 1, 1), BreadthRegime.RED, 0, 1));

        BreadthDailySnapshotRepository repository = fakeRepository(snapshots);
        BreadthFilteredMomentumBacktestService service = new BreadthFilteredMomentumBacktestService(
                new RiskAdjustedMomentumBacktestService(fixtureCache()), repository);

        BreadthFilteredMomentumBacktestRequest request = new BreadthFilteredMomentumBacktestRequest(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1), 1_000_000, 10, 20, "NIFTY 500",
                .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0, "ATR", 0, 14, 3, 2, 200, 20, 50,
                NiftyIndexName.NIFTY500, BreadthEntryMode.GREEN_ONLY, 0);

        BreadthFilteredMomentumBacktestResult result = service.run(request);

        assertThat(result.breadthFiltered().regimeExposureHistory()).isNotEmpty()
                .allSatisfy(point -> assertThat(point.newBuysAllowed()).isTrue());
    }

    @Test
    void baselineModeReturnsTheSameResultForBothFields() {
        BreadthDailySnapshotRepository repository = fakeRepository(List.of());
        BreadthFilteredMomentumBacktestService service = new BreadthFilteredMomentumBacktestService(
                new RiskAdjustedMomentumBacktestService(fixtureCache()), repository);

        BreadthFilteredMomentumBacktestRequest request = new BreadthFilteredMomentumBacktestRequest(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1), 1_000_000, 10, 20, "NIFTY 500",
                .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0, "ATR", 0, 14, 3, 2, 200, 20, 50,
                NiftyIndexName.NIFTY500, BreadthEntryMode.BASELINE, 0);

        BreadthFilteredMomentumBacktestResult result = service.run(request);

        assertThat(result.breadthFiltered()).isEqualTo(result.baseline());
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    private BreadthDailySnapshot snapshot(NiftyIndexName universe, LocalDate date, BreadthRegime regime, double score, int version) {
        BreadthDailySnapshot snapshot = new BreadthDailySnapshot();
        snapshot.setUniverse(universe);
        snapshot.setTradingDate(date);
        snapshot.setMethodology(BreadthMethodology.CURRENT_CONSTITUENTS);
        snapshot.setScoreConfigurationVersion(version);
        snapshot.setCoveragePercent(100);
        snapshot.setQualityStatus(BreadthSnapshotQuality.VALID);
        snapshot.setFinalScore(score);
        snapshot.setRegime(regime);
        snapshot.setIndicatorValues(Map.of());
        snapshot.setComponentScores(Map.of());
        snapshot.setComponentReasons(Map.of());
        return snapshot;
    }

    private StockPriceCacheService fixtureCache() {
        Map<String, List<OHLCV>> stocks = new LinkedHashMap<>();
        for (int i = 1; i <= 24; i++) stocks.put("S" + i, bars(100 + i, i * .025));
        Map<String, List<OHLCV>> indexes = Map.of("NIFTY500", bars(1000, .15));
        return new StockPriceCacheService(null) {
            @Override public Map<String, List<OHLCV>> getCachedAllStockPriceData() { return stocks; }
            @Override public Map<String, List<OHLCV>> getCachedAllIndexPriceData() { return indexes; }
        };
    }

    private List<OHLCV> bars(double start, double step) {
        List<OHLCV> result = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 700; i++) {
            double close = start + i * step;
            result.add(new OHLCV(Date.valueOf(date.plusDays(i)), close, close + 1, close - 1, close, 10000 + i));
        }
        return result;
    }

    /** Stubs only the two lookup methods {@code buildAdapter} actually calls, backed by the given fixed snapshot list. */
    private BreadthDailySnapshotRepository fakeRepository(List<BreadthDailySnapshot> snapshots) {
        BreadthDailySnapshotRepository repository = mock(BreadthDailySnapshotRepository.class);
        when(repository.findByUniverseAndTradingDate(any(), any())).thenAnswer(invocation -> {
            NiftyIndexName universe = invocation.getArgument(0);
            LocalDate tradingDate = invocation.getArgument(1);
            return snapshots.stream().filter(s -> s.getUniverse() == universe && s.getTradingDate().equals(tradingDate)).findFirst();
        });
        when(repository.findFirstByUniverseAndTradingDateLessThanOrderByTradingDateDesc(any(), any())).thenAnswer(invocation -> {
            NiftyIndexName universe = invocation.getArgument(0);
            LocalDate tradingDate = invocation.getArgument(1);
            return snapshots.stream().filter(s -> s.getUniverse() == universe && s.getTradingDate().isBefore(tradingDate))
                    .max(Comparator.comparing(BreadthDailySnapshot::getTradingDate));
        });
        return repository;
    }
}
