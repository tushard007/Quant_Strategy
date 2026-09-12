package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.service.MarketBreadthQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MarketBreadthQueryServiceTest {

    @Test
    void returnsLatestSnapshotAndSectorStatus() {
        BreadthDailySnapshot snapshot = snapshot(LocalDate.of(2026, 9, 11));
        BreadthDailySnapshotRepository repository = TestRepositoryProxy.create(
                BreadthDailySnapshotRepository.class, (method, arguments) ->
                        method.getName().equals("findFirstByUniverseOrderByTradingDateDesc")
                                ? Optional.of(snapshot) : null);
        MarketBreadthQueryService service = new MarketBreadthQueryService(repository);

        var latest = service.latest(NiftyIndexName.NIFTY500);
        var sectors = service.sectors(NiftyIndexName.NIFTY500);

        assertThat(latest.tradingDate()).isEqualTo(snapshot.getTradingDate());
        assertThat(latest.coveragePercent()).isEqualTo(94.5);
        assertThat(latest.methodology()).isEqualTo(BreadthMethodology.CURRENT_CONSTITUENTS);
        assertThat(sectors.participatingSectors()).isEqualTo(1);
        assertThat(sectors.availableSectors()).isEqualTo(11);
        assertThat(sectors.sectors()).anySatisfy(sector -> {
            assertThat(sector.symbol()).isEqualTo("NIFTY BANK");
            assertThat(sector.above50DayEma()).isTrue();
            assertThat(sector.distancePercent()).isEqualTo(2.75);
        });
    }

    @Test
    void appliesRequestedHistoryLimitAndValidatesTheRange() {
        BreadthDailySnapshot first = snapshot(LocalDate.of(2026, 9, 10));
        BreadthDailySnapshot second = snapshot(LocalDate.of(2026, 9, 11));
        BreadthDailySnapshotRepository repository = TestRepositoryProxy.create(
                BreadthDailySnapshotRepository.class, (method, arguments) ->
                        method.getName().equals("findByUniverseAndTradingDateBetweenOrderByTradingDateAsc")
                                && arguments.length == 4 ? List.of(first, second) : null);
        MarketBreadthQueryService service = new MarketBreadthQueryService(repository);

        assertThat(service.history(NiftyIndexName.NIFTY500, first.getTradingDate(),
                second.getTradingDate(), 25)).extracting(response -> response.tradingDate())
                .containsExactly(first.getTradingDate(), second.getTradingDate());
        assertThatIllegalArgumentException().isThrownBy(() -> service.history(NiftyIndexName.NIFTY500,
                second.getTradingDate(), first.getTradingDate(), 25));
        assertThatIllegalArgumentException().isThrownBy(() -> service.history(NiftyIndexName.NIFTY500,
                first.getTradingDate(), second.getTradingDate(), MarketBreadthQueryService.MAX_HISTORY_LIMIT + 1));
    }

    @Test
    void returnsSnapshotAndSectorsAsOfTheSelectedDate() {
        LocalDate selectedDate = LocalDate.of(2026, 8, 14);
        BreadthDailySnapshot snapshot = snapshot(selectedDate);
        BreadthDailySnapshotRepository repository = TestRepositoryProxy.create(
                BreadthDailySnapshotRepository.class, (method, arguments) ->
                        method.getName().equals(
                                "findFirstByUniverseAndTradingDateLessThanEqualOrderByTradingDateDesc")
                                ? Optional.of(snapshot) : null);
        MarketBreadthQueryService service = new MarketBreadthQueryService(repository);

        assertThat(service.latest(NiftyIndexName.NIFTY500, selectedDate).tradingDate())
                .isEqualTo(selectedDate);
        assertThat(service.sectors(NiftyIndexName.NIFTY500, selectedDate).tradingDate())
                .isEqualTo(selectedDate);
    }

    private BreadthDailySnapshot snapshot(LocalDate date) {
        BreadthDailySnapshot snapshot = new BreadthDailySnapshot();
        snapshot.setUniverse(NiftyIndexName.NIFTY500);
        snapshot.setTradingDate(date);
        snapshot.setMethodology(BreadthMethodology.CURRENT_CONSTITUENTS);
        snapshot.setScoreConfigurationVersion(1);
        snapshot.setCoveragePercent(94.5);
        snapshot.setQualityStatus(BreadthSnapshotQuality.VALID);
        snapshot.setFinalScore(72.0);
        snapshot.setRegime(BreadthRegime.GREEN);
        snapshot.setIndicatorValues(Map.of(
                BreadthMetricKeys.SECTOR_PARTICIPATION, 1.0,
                BreadthMetricKeys.SECTOR_COUNT, 11.0,
                "SECTOR_NIFTY_BANK_ABOVE_50D", 1.0,
                "SECTOR_NIFTY_BANK_DISTANCE_PERCENT", 2.75));
        snapshot.setComponentScores(Map.of("BREADTH_50D", 8.0));
        snapshot.setComponentReasons(Map.of("BREADTH_50D", "Broad participation"));
        return snapshot;
    }
}
