package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthFilteredMomentumBacktestRequest;
import org.factor_investing.quant_strategy.model.request.BreadthThresholdSensitivityRequest;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.BreadthThresholdSensitivityCell;
import org.factor_investing.quant_strategy.model.response.BreadthThresholdSensitivityResult;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a grid of breadth entry modes / score cutoffs / universes / trend-basis choices and reports baseline
 * vs. filtered performance per cell, so overfitting risk from cherry-picking a single cutoff is visible (BRD-093).
 */
@Service
public class BreadthThresholdSensitivityService {
    static final int MAX_GRID_CELLS = 60;
    static final long MIN_QUALIFYING_SAMPLE_COUNT = 12;

    private final BreadthFilteredMomentumBacktestService breadthFilteredService;

    public BreadthThresholdSensitivityService(BreadthFilteredMomentumBacktestService breadthFilteredService) {
        this.breadthFilteredService = breadthFilteredService;
    }

    public BreadthThresholdSensitivityResult run(BreadthThresholdSensitivityRequest request) {
        List<NiftyIndexName> universes = request.universes();
        List<BreadthEntryMode> entryModes = request.entryModes();
        List<Double> scoreCutoffs = request.scoreCutoffs();
        List<SensitivityMaBasis> maBases = request.movingAverageBasesToCompare();
        if (universes == null || universes.isEmpty() || entryModes == null || entryModes.isEmpty()
                || scoreCutoffs == null || scoreCutoffs.isEmpty() || maBases == null || maBases.isEmpty())
            throw new IllegalArgumentException("universes, entryModes, scoreCutoffs and movingAverageBasesToCompare must each be non-empty");

        long totalCells = (long) universes.size() * entryModes.size() * scoreCutoffs.size() * maBases.size();
        if (totalCells > MAX_GRID_CELLS)
            throw new IllegalArgumentException("Sensitivity grid has " + totalCells + " cells, exceeding the maximum of " + MAX_GRID_CELLS);

        List<BreadthThresholdSensitivityCell> cells = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (NiftyIndexName universe : universes) {
            for (BreadthEntryMode entryMode : entryModes) {
                for (double scoreCutoff : scoreCutoffs) {
                    for (SensitivityMaBasis maBasis : maBases) {
                        BreadthFilteredMomentumBacktestResult result = evaluate(request, universe, entryMode, scoreCutoff, maBasis);
                        long qualifyingSampleCount = result.breadthFiltered().regimeExposureHistory().stream()
                                .filter(RiskAdjustedMomentumBacktestResult.RegimePoint::newBuysAllowed).count();
                        if (qualifyingSampleCount < MIN_QUALIFYING_SAMPLE_COUNT) {
                            warnings.add("Skipped cell [universe=" + universe + ", entryMode=" + entryMode + ", scoreCutoff="
                                    + scoreCutoff + ", maBasis=" + maBasis + "]: only " + qualifyingSampleCount
                                    + " qualifying periods (minimum " + MIN_QUALIFYING_SAMPLE_COUNT + ")");
                            continue;
                        }
                        RiskAdjustedMomentumBacktestResult baseline = result.baseline();
                        RiskAdjustedMomentumBacktestResult filtered = result.breadthFiltered();
                        cells.add(new BreadthThresholdSensitivityCell(universe, entryMode, scoreCutoff, maBasis,
                                baseline.totalReturn(), baseline.cagr(), baseline.sharpeRatio(), baseline.maximumDrawdown(),
                                filtered.totalReturn(), filtered.cagr(), filtered.sharpeRatio(), filtered.maximumDrawdown(),
                                qualifyingSampleCount, filtered.rebalanceCount()));
                    }
                }
            }
        }
        return new BreadthThresholdSensitivityResult(request, cells, warnings);
    }

    /**
     * {@link SensitivityMaBasis#PERSISTED_BREADTH_SCORE} filters by the real, persisted EMA-based breadth
     * snapshot for {@code entryMode}/{@code scoreCutoff}. {@link SensitivityMaBasis#LEGACY_SMA_OVERLAY} ignores
     * {@code entryMode}/{@code scoreCutoff} entirely and instead reports the momentum service's own built-in
     * SMA-based benchmark/breadth gate (i.e. its plain baseline run) as the "filtered" side of the comparison.
     */
    private BreadthFilteredMomentumBacktestResult evaluate(BreadthThresholdSensitivityRequest request, NiftyIndexName universe,
            BreadthEntryMode entryMode, double scoreCutoff, SensitivityMaBasis maBasis) {
        BreadthEntryMode effectiveMode = maBasis == SensitivityMaBasis.LEGACY_SMA_OVERLAY ? BreadthEntryMode.BASELINE : entryMode;
        BreadthFilteredMomentumBacktestRequest filteredRequest = new BreadthFilteredMomentumBacktestRequest(
                request.startDate(), request.endDate(), request.initialCapital(), request.entryRank(), request.retentionRank(),
                request.benchmark(), request.transactionCostPercent(), request.slippagePercent(), request.riskFreeRatePercent(),
                request.rebalanceMode(), request.bufferAmount(), request.maximumLeverageAmount(), request.borrowingInterestRatePercent(),
                request.stopModel(), request.trailingStopPercent(), request.atrPeriod(), request.atrMultiplier(), request.cooldownWeeks(),
                request.benchmarkSmaPeriod(), request.breadthThresholdPercent(), request.weakExposureCapPercent(),
                universe, effectiveMode, scoreCutoff);
        return breadthFilteredService.run(filteredRequest);
    }
}
