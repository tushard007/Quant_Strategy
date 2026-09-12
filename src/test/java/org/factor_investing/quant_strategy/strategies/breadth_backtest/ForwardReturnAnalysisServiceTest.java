package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.ForwardReturnAnalysisResult;
import org.factor_investing.quant_strategy.model.response.ForwardReturnStatistics;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForwardReturnAnalysisServiceTest {

    @Test
    void bucketBoundariesClampCorrectly() {
        assertThat(ForwardReturnAnalysisService.scoreBucket(0)).isEqualTo(0);
        assertThat(ForwardReturnAnalysisService.scoreBucket(9.99)).isEqualTo(0);
        assertThat(ForwardReturnAnalysisService.scoreBucket(10)).isEqualTo(10);
        assertThat(ForwardReturnAnalysisService.scoreBucket(100)).isEqualTo(90);
    }

    @Test
    void computesCloseToCloseForwardReturnsAtBothHorizons() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<OHLCV> series = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double close = 100 + i;
            series.add(new OHLCV(Date.valueOf(start.plusDays(i)), close, close + 1, close - 1, close, 1000 + i));
        }
        StockPriceCacheService cache = mock(StockPriceCacheService.class);
        when(cache.getCachedAllIndexPriceData()).thenReturn(Map.of("NIFTY500", series));

        LocalDate snapshotDate = start.plusDays(5);
        BreadthDailySnapshot snapshot = snapshot(snapshotDate, BreadthRegime.GREEN, 55);
        BreadthDailySnapshotRepository repository = mock(BreadthDailySnapshotRepository.class);
        when(repository.findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(NiftyIndexName.NIFTY500, start, start.plusDays(30)))
                .thenReturn(List.of(snapshot));

        ForwardReturnAnalysisService service = new ForwardReturnAnalysisService(repository, cache);
        ForwardReturnAnalysisResult result = service.analyze(NiftyIndexName.NIFTY500, start, start.plusDays(30), "NIFTY 500");

        assertThat(result.totalObservations()).isEqualTo(1);
        assertThat(result.observationsMissingForward20()).isZero();
        // base close = 100+5=105; +20 sessions -> close 125; +60 sessions -> out of range (only 200 bars, base index 5, need 65)
        List<ForwardReturnStatistics> byRegime = result.byRegime();
        assertThat(byRegime).hasSize(1);
        ForwardReturnStatistics stats = byRegime.getFirst();
        assertThat(stats.groupKey()).isEqualTo("GREEN");
        assertThat(stats.meanReturn20()).isCloseTo(20.0 / 105.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void marksObservationsMissingForwardReturnsWhenNotEnoughFutureBarsExist() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<OHLCV> series = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double close = 100 + i;
            series.add(new OHLCV(Date.valueOf(start.plusDays(i)), close, close + 1, close - 1, close, 1000 + i));
        }
        StockPriceCacheService cache = mock(StockPriceCacheService.class);
        when(cache.getCachedAllIndexPriceData()).thenReturn(Map.of("NIFTY500", series));

        BreadthDailySnapshot snapshot = snapshot(start.plusDays(5), BreadthRegime.AMBER, 45);
        BreadthDailySnapshotRepository repository = mock(BreadthDailySnapshotRepository.class);
        when(repository.findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(NiftyIndexName.NIFTY500, start, start.plusDays(9)))
                .thenReturn(List.of(snapshot));

        ForwardReturnAnalysisService service = new ForwardReturnAnalysisService(repository, cache);
        ForwardReturnAnalysisResult result = service.analyze(NiftyIndexName.NIFTY500, start, start.plusDays(9), "NIFTY 500");

        assertThat(result.observationsMissingForward20()).isEqualTo(1);
        assertThat(result.observationsMissingForward60()).isEqualTo(1);
    }

    private BreadthDailySnapshot snapshot(LocalDate date, BreadthRegime regime, double score) {
        BreadthDailySnapshot snapshot = new BreadthDailySnapshot();
        snapshot.setUniverse(NiftyIndexName.NIFTY500);
        snapshot.setTradingDate(date);
        snapshot.setMethodology(BreadthMethodology.CURRENT_CONSTITUENTS);
        snapshot.setScoreConfigurationVersion(1);
        snapshot.setCoveragePercent(100);
        snapshot.setQualityStatus(BreadthSnapshotQuality.VALID);
        snapshot.setFinalScore(score);
        snapshot.setRegime(regime);
        snapshot.setIndicatorValues(Map.of());
        snapshot.setComponentScores(Map.of());
        snapshot.setComponentReasons(Map.of());
        return snapshot;
    }
}
