package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthFilteredMomentumBacktestRequest;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult.BreadthCoverageNote;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.BreadthExposureAdapter;
import org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum.RiskAdjustedMomentumBacktestService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Wraps {@link RiskAdjustedMomentumBacktestService} to gate/scale its per-period exposure by the persisted
 * market-breadth regime/score, without duplicating its trading loop. No-look-ahead guarantee: for a given
 * {@code signalDate}, only a snapshot dated on or strictly before that date is ever consulted
 * ({@link #snapshotOnOrBefore}), mirroring the momentum service's own next-open execution convention.
 */
@Service
public class BreadthFilteredMomentumBacktestService {
    private final RiskAdjustedMomentumBacktestService momentumService;
    private final BreadthDailySnapshotRepository snapshotRepository;

    public BreadthFilteredMomentumBacktestService(RiskAdjustedMomentumBacktestService momentumService,
                                                    BreadthDailySnapshotRepository snapshotRepository) {
        this.momentumService = momentumService;
        this.snapshotRepository = snapshotRepository;
    }

    public BreadthFilteredMomentumBacktestResult run(BreadthFilteredMomentumBacktestRequest request) {
        RiskAdjustedMomentumBacktestResult baseline = momentumService.run(request.startDate(), request.endDate(),
                request.initialCapital(), request.entryRank(), request.retentionRank(), request.benchmark(),
                request.transactionCostPercent(), request.slippagePercent(), request.riskFreeRatePercent(),
                request.rebalanceMode(), request.bufferAmount(), request.maximumLeverageAmount(),
                request.borrowingInterestRatePercent(), request.stopModel(), request.trailingStopPercent(),
                request.atrPeriod(), request.atrMultiplier(), request.cooldownWeeks(), request.benchmarkSmaPeriod(),
                request.breadthThresholdPercent(), request.weakExposureCapPercent());

        if (request.breadthEntryMode() == BreadthEntryMode.BASELINE) {
            return new BreadthFilteredMomentumBacktestResult(baseline, baseline, request.breadthUniverse(),
                    BreadthEntryMode.BASELINE, request.breadthScoreCutoff(), BreadthMethodology.CURRENT_CONSTITUENTS,
                    0, List.of(), List.of());
        }

        List<BreadthCoverageNote> coverageNotes = new ArrayList<>();
        TreeSet<Integer> versionsObserved = new TreeSet<>();
        int[] representativeVersion = {0};
        BreadthMethodology[] representativeMethodology = {BreadthMethodology.CURRENT_CONSTITUENTS};
        BreadthExposureAdapter adapter = buildAdapter(request.breadthUniverse(), request.breadthEntryMode(),
                request.breadthScoreCutoff(), coverageNotes, versionsObserved, representativeVersion, representativeMethodology);

        RiskAdjustedMomentumBacktestResult filtered = momentumService.runWithBreadthAdapter(request.startDate(),
                request.endDate(), request.initialCapital(), request.entryRank(), request.retentionRank(),
                request.benchmark(), request.transactionCostPercent(), request.slippagePercent(),
                request.riskFreeRatePercent(), request.rebalanceMode(), request.bufferAmount(),
                request.maximumLeverageAmount(), request.borrowingInterestRatePercent(), request.stopModel(),
                request.trailingStopPercent(), request.atrPeriod(), request.atrMultiplier(), request.cooldownWeeks(),
                request.benchmarkSmaPeriod(), request.breadthThresholdPercent(), request.weakExposureCapPercent(),
                adapter, false);

        return new BreadthFilteredMomentumBacktestResult(baseline, filtered, request.breadthUniverse(),
                request.breadthEntryMode(), request.breadthScoreCutoff(), representativeMethodology[0],
                representativeVersion[0], List.copyOf(versionsObserved), coverageNotes);
    }

    private BreadthExposureAdapter buildAdapter(NiftyIndexName universe, BreadthEntryMode mode, double scoreCutoff,
                                                 List<BreadthCoverageNote> coverageNotes, TreeSet<Integer> versionsObserved,
                                                 int[] representativeVersion, BreadthMethodology[] representativeMethodology) {
        return (signalDate, baselineExposureCapPercent, baselineNewBuysAllowed) -> {
            Optional<BreadthDailySnapshot> snapshot = snapshotOnOrBefore(universe, signalDate);
            if (snapshot.isEmpty()) {
                coverageNotes.add(new BreadthCoverageNote(signalDate,
                        "No breadth snapshot available on or before this date; baseline exposure applied"));
                return new BreadthExposureAdapter.ExposureDecision(baselineExposureCapPercent, baselineNewBuysAllowed);
            }
            BreadthDailySnapshot s = snapshot.get();
            versionsObserved.add(s.getScoreConfigurationVersion());
            representativeVersion[0] = s.getScoreConfigurationVersion();
            representativeMethodology[0] = s.getMethodology();
            boolean scoreCutoffMet = s.getFinalScore() >= scoreCutoff;
            double clampedScoreFraction = Math.max(0, Math.min(100, s.getFinalScore())) / 100.0;
            return switch (mode) {
                case GREEN_ONLY -> new BreadthExposureAdapter.ExposureDecision(baselineExposureCapPercent,
                        baselineNewBuysAllowed && s.getRegime() == BreadthRegime.GREEN && scoreCutoffMet);
                case GREEN_AMBER -> new BreadthExposureAdapter.ExposureDecision(baselineExposureCapPercent,
                        baselineNewBuysAllowed && s.getRegime() != BreadthRegime.RED && scoreCutoffMet);
                case SCORE_SCALED -> new BreadthExposureAdapter.ExposureDecision(
                        baselineExposureCapPercent * clampedScoreFraction, baselineNewBuysAllowed && scoreCutoffMet);
                case BASELINE -> new BreadthExposureAdapter.ExposureDecision(baselineExposureCapPercent, baselineNewBuysAllowed);
            };
        };
    }

    private Optional<BreadthDailySnapshot> snapshotOnOrBefore(NiftyIndexName universe, LocalDate signalDate) {
        return snapshotRepository.findByUniverseAndTradingDate(universe, signalDate)
                .or(() -> snapshotRepository.findFirstByUniverseAndTradingDateLessThanOrderByTradingDateDesc(universe, signalDate));
    }
}
