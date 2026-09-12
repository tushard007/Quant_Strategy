package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.repository.BreadthScoreConfigurationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBreadthCalculationOrchestratorTest {

    @Test
    void recalculationUpdatesTheExistingUniverseDateSnapshotInsteadOfCreatingAnother() {
        LocalDate date = LocalDate.of(2025, 1, 31);
        AlignedBreadthDataset dataset = new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, date, List.of(date), List.of(),
                java.util.Map.of(), List.of());
        BreadthDatasetBuilderService datasetBuilder = new BreadthDatasetBuilderService(null, null, null) {
            @Override
            public List<AlignedBreadthDataset> buildRange(NiftyIndexName universe, BreadthMethodology methodology,
                                                           LocalDate from, LocalDate to) {
                return List.of(dataset);
            }
        };
        BreadthIndexSeries indexSeries = new BreadthIndexSeries(new TreeMap<>(),
                new EnumMap<>(RequiredMarketBreadthIndex.class), new TreeMap<>());
        BreadthIndexDataService indexDataService = new BreadthIndexDataService(null) {
            @Override
            public BreadthIndexSeries load(NiftyIndexName universe) {
                return indexSeries;
            }
        };
        List<BreadthDailySnapshot> stored = new ArrayList<>();
        BreadthDailySnapshotRepository snapshotRepository = TestRepositoryProxy.create(
                BreadthDailySnapshotRepository.class, (method, arguments) -> switch (method.getName()) {
                    case "findFirstByUniverseAndTradingDateLessThanOrderByTradingDateDesc" -> Optional.empty();
                    case "findByUniverseAndTradingDateBetweenOrderByTradingDateAsc" -> List.copyOf(stored);
                    case "saveAll" -> {
                        stored.clear();
                        ((Iterable<?>) arguments[0]).forEach(item -> stored.add((BreadthDailySnapshot) item));
                        yield List.copyOf(stored);
                    }
                    default -> null;
                });
        BreadthScoreConfigurationRepository configurationRepository = TestRepositoryProxy.create(
                BreadthScoreConfigurationRepository.class,
                (method, arguments) -> method.getName().equals("findByActiveTrue") ? Optional.empty() : null);
        BreadthDataQualityService qualityService = new BreadthDataQualityService();
        MarketBreadthCalculationService calculationService = new MarketBreadthCalculationService(
                new BreadthDailyStatisticsService(), new BreadthCoverageService(qualityService), qualityService,
                new BreadthScoreService());
        MarketBreadthCalculationOrchestrator orchestrator = new MarketBreadthCalculationOrchestrator(
                datasetBuilder, indexDataService, calculationService, snapshotRepository, configurationRepository);

        orchestrator.calculate(date, NiftyIndexName.NIFTY500, BreadthMethodology.CURRENT_CONSTITUENTS);
        BreadthDailySnapshot first = stored.getFirst();
        orchestrator.calculate(date, NiftyIndexName.NIFTY500, BreadthMethodology.CURRENT_CONSTITUENTS);

        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst()).isSameAs(first);
        assertThat(first.getTradingDate()).isEqualTo(date);
        assertThat(first.getIndicatorValues()).containsKeys(BreadthMetricKeys.AD_LINE,
                BreadthMetricKeys.MCCLELLAN_OSCILLATOR, BreadthMetricKeys.SECTOR_PARTICIPATION);
        assertThat(first.getComponentReasons()).isNotEmpty();
    }
}
