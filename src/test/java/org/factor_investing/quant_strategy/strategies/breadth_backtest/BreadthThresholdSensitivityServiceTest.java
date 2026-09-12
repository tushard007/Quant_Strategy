package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.request.BreadthThresholdSensitivityRequest;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.BreadthThresholdSensitivityResult;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BreadthThresholdSensitivityServiceTest {

    @Test
    void rejectsGridsLargerThanTheMaximumCellCount() {
        BreadthFilteredMomentumBacktestService backtestService = mock(BreadthFilteredMomentumBacktestService.class);
        BreadthThresholdSensitivityService service = new BreadthThresholdSensitivityService(backtestService);

        BreadthThresholdSensitivityRequest request = request(
                List.of(NiftyIndexName.NIFTY500, NiftyIndexName.NIFTY200, NiftyIndexName.NIFTY50, NiftyIndexName.NIFTY750),
                List.of(BreadthEntryMode.GREEN_ONLY, BreadthEntryMode.GREEN_AMBER, BreadthEntryMode.SCORE_SCALED),
                List.of(10.0, 20.0, 30.0, 40.0, 50.0),
                List.of(SensitivityMaBasis.PERSISTED_BREADTH_SCORE, SensitivityMaBasis.LEGACY_SMA_OVERLAY));

        assertThatThrownBy(() -> service.run(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeding the maximum of " + BreadthThresholdSensitivityService.MAX_GRID_CELLS);
    }

    @Test
    void excludesThinCellsWithAWarningInsteadOfReportingThem() {
        BreadthFilteredMomentumBacktestService backtestService = mock(BreadthFilteredMomentumBacktestService.class);
        when(backtestService.run(any())).thenReturn(fixtureResult(5));
        BreadthThresholdSensitivityService service = new BreadthThresholdSensitivityService(backtestService);

        BreadthThresholdSensitivityRequest request = request(
                List.of(NiftyIndexName.NIFTY500), List.of(BreadthEntryMode.GREEN_ONLY), List.of(80.0),
                List.of(SensitivityMaBasis.PERSISTED_BREADTH_SCORE));

        BreadthThresholdSensitivityResult result = service.run(request);

        assertThat(result.cells()).isEmpty();
        assertThat(result.warnings()).hasSize(1).first().asString().contains("only 5 qualifying periods");
    }

    @Test
    void includesCellsMeetingTheMinimumQualifyingSampleCount() {
        BreadthFilteredMomentumBacktestService backtestService = mock(BreadthFilteredMomentumBacktestService.class);
        when(backtestService.run(any())).thenReturn(fixtureResult(30));
        BreadthThresholdSensitivityService service = new BreadthThresholdSensitivityService(backtestService);

        BreadthThresholdSensitivityRequest request = request(
                List.of(NiftyIndexName.NIFTY500), List.of(BreadthEntryMode.GREEN_ONLY), List.of(20.0),
                List.of(SensitivityMaBasis.PERSISTED_BREADTH_SCORE));

        BreadthThresholdSensitivityResult result = service.run(request);

        assertThat(result.cells()).hasSize(1);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.cells().getFirst().qualifyingSnapshotSampleCount()).isEqualTo(30);
    }

    private BreadthFilteredMomentumBacktestResult fixtureResult(int qualifyingNewBuyCount) {
        RiskAdjustedMomentumBacktestResult.RegimePoint allowed =
                new RiskAdjustedMomentumBacktestResult.RegimePoint(LocalDate.of(2025, 1, 1), 80, true, 100, true);
        RiskAdjustedMomentumBacktestResult.RegimePoint blocked =
                new RiskAdjustedMomentumBacktestResult.RegimePoint(LocalDate.of(2025, 2, 1), 20, true, 100, false);
        List<RiskAdjustedMomentumBacktestResult.RegimePoint> history = new ArrayList<>();
        for (int i = 0; i < qualifyingNewBuyCount; i++) history.add(allowed);
        history.add(blocked);

        RiskAdjustedMomentumBacktestResult filtered = backtestResult(10.0, 5.0, 1.0, -15.0, qualifyingNewBuyCount, history);
        RiskAdjustedMomentumBacktestResult baseline = backtestResult(8.0, 4.0, 0.8, -20.0, qualifyingNewBuyCount, List.of());

        return new BreadthFilteredMomentumBacktestResult(baseline, filtered, NiftyIndexName.NIFTY500,
                BreadthEntryMode.GREEN_ONLY, 20, BreadthMethodology.CURRENT_CONSTITUENTS, 1, List.of(1), List.of());
    }

    private RiskAdjustedMomentumBacktestResult backtestResult(double totalReturn, double cagr, double sharpeRatio,
            double maximumDrawdown, int rebalanceCount, List<RiskAdjustedMomentumBacktestResult.RegimePoint> regimeExposureHistory) {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2026, 8, 1);
        return new RiskAdjustedMomentumBacktestResult(start, end, 1_000_000, 1_000_000 * (1 + totalReturn / 100),
                totalReturn, cagr, maximumDrawdown, "NIFTY 500", 1_000_000, 0, 0, 0, 10, sharpeRatio, 0, 0, 0, 0, 6.5,
                "REPLACEMENT_ONLY", 0, 0, 0, 0, 0, 0, 0, 0, rebalanceCount, 0, 0, "ATR", 0, 3, 200, 20, 50,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                regimeExposureHistory);
    }

    private BreadthThresholdSensitivityRequest request(List<NiftyIndexName> universes, List<BreadthEntryMode> entryModes,
            List<Double> scoreCutoffs, List<SensitivityMaBasis> maBases) {
        return new BreadthThresholdSensitivityRequest(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1), 1_000_000,
                10, 20, "NIFTY 500", .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0, "ATR", 0, 14, 3, 2, 200, 20, 50,
                universes, entryModes, scoreCutoffs, maBases);
    }
}
