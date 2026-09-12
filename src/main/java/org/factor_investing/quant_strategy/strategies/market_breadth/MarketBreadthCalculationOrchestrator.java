package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.repository.BreadthScoreConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Range/single-date orchestration and idempotent batch persistence for BRD-041. */
@Service
public class MarketBreadthCalculationOrchestrator {
    private static final int RECURSIVE_INDICATOR_WARMUP_CALENDAR_DAYS = 120;

    private final BreadthDatasetBuilderService datasetBuilderService;
    private final BreadthIndexDataService indexDataService;
    private final MarketBreadthCalculationService calculationService;
    private final BreadthDailySnapshotRepository snapshotRepository;
    private final BreadthScoreConfigurationRepository configurationRepository;

    public MarketBreadthCalculationOrchestrator(BreadthDatasetBuilderService datasetBuilderService,
                                                 BreadthIndexDataService indexDataService,
                                                 MarketBreadthCalculationService calculationService,
                                                 BreadthDailySnapshotRepository snapshotRepository,
                                                 BreadthScoreConfigurationRepository configurationRepository) {
        this.datasetBuilderService = datasetBuilderService;
        this.indexDataService = indexDataService;
        this.calculationService = calculationService;
        this.snapshotRepository = snapshotRepository;
        this.configurationRepository = configurationRepository;
    }

    @Transactional
    public List<BreadthCalculationResult> calculate(LocalDate date, NiftyIndexName universe,
                                                     BreadthMethodology methodology) {
        return calculate(date, date, universe, methodology);
    }

    @Transactional
    public List<BreadthCalculationResult> calculate(LocalDate from, LocalDate to, NiftyIndexName universe,
                                                     BreadthMethodology methodology) {
        if (from == null || to == null || universe == null || methodology == null) {
            throw new IllegalArgumentException("Breadth date range, universe, and methodology are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Breadth calculation start date must not be after end date");
        }
        if (!List.of(NiftyIndexName.NIFTY50, NiftyIndexName.NIFTY200, NiftyIndexName.NIFTY500).contains(universe)) {
            throw new IllegalArgumentException("Breadth calculation supports NIFTY50, NIFTY200, or NIFTY500");
        }

        BreadthScoreParameters parameters = configurationRepository.findByActiveTrue()
                .map(BreadthScoreParameters::from).orElseGet(BreadthScoreParameters::defaults);
        LocalDate calculationStart = from.minusDays(RECURSIVE_INDICATOR_WARMUP_CALENDAR_DAYS);
        List<AlignedBreadthDataset> datasets = datasetBuilderService
                .buildRange(universe, methodology, calculationStart, to);
        BreadthCalculationSeed seed = snapshotRepository
                .findFirstByUniverseAndTradingDateLessThanOrderByTradingDateDesc(universe, calculationStart)
                .filter(snapshot -> snapshot.getMethodology() == methodology)
                .map(snapshot -> BreadthCalculationSeed.from(snapshot.getIndicatorValues()))
                .orElseGet(BreadthCalculationSeed::empty);
        List<BreadthCalculationResult> calculated = calculationService.calculate(datasets,
                indexDataService.load(universe), parameters, seed);
        List<BreadthCalculationResult> requested = calculated.stream()
                .filter(result -> !result.tradingDate().isBefore(from) && !result.tradingDate().isAfter(to))
                .toList();

        Map<LocalDate, BreadthDailySnapshot> existing = snapshotRepository
                .findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(universe, from, to).stream()
                .collect(Collectors.toMap(BreadthDailySnapshot::getTradingDate, Function.identity()));
        List<BreadthDailySnapshot> snapshots = requested.stream()
                .map(result -> toSnapshot(result, parameters.version(), existing.get(result.tradingDate())))
                .toList();
        snapshotRepository.saveAll(snapshots);
        return requested;
    }

    private BreadthDailySnapshot toSnapshot(BreadthCalculationResult result, int configurationVersion,
                                             BreadthDailySnapshot snapshot) {
        BreadthDailySnapshot target = snapshot == null ? new BreadthDailySnapshot() : snapshot;
        target.setUniverse(result.universe());
        target.setTradingDate(result.tradingDate());
        target.setMethodology(result.methodology());
        target.setScoreConfigurationVersion(configurationVersion);
        target.setCoveragePercent(result.coverage().coveragePercent());
        target.setQualityStatus(result.coverage().qualityStatus());
        target.setFinalScore(result.score().score());
        target.setRegime(result.score().regime());
        target.setIndicatorValues(result.indicatorValues());
        target.setComponentScores(result.score().componentScores());
        target.setComponentReasons(result.score().componentReasons());
        return target;
    }
}
