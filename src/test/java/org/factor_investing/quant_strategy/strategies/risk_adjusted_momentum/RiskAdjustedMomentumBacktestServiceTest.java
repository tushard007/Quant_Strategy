package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAdjustedMomentumBacktestServiceTest {

    /**
     * Regression guard for the additive {@link BreadthExposureAdapter} hook: existing {@code run(...)} callers
     * never construct an adapter, so passing {@code null} through {@code runCore}/{@code addDiagnostics} must
     * reproduce byte-identical output to the plain overload.
     */
    @Test
    void runWithNullBreadthAdapterIsIdenticalToPlainRun() {
        StockPriceCacheService cache = fixtureCache();
        RiskAdjustedMomentumBacktestService service = new RiskAdjustedMomentumBacktestService(cache);

        RiskAdjustedMomentumBacktestResult plain = service.run(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 8, 1),
                1_000_000, 10, 20, "NIFTY 500", .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0,
                RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL, RiskAdjustedMomentumConstants.DEFAULT_TRAILING_STOP_PERCENT,
                RiskAdjustedMomentumConstants.DEFAULT_ATR_PERIOD, RiskAdjustedMomentumConstants.DEFAULT_ATR_MULTIPLIER,
                RiskAdjustedMomentumConstants.DEFAULT_COOLDOWN_WEEKS, RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK_SMA_PERIOD,
                RiskAdjustedMomentumConstants.DEFAULT_BREADTH_THRESHOLD_PERCENT, RiskAdjustedMomentumConstants.DEFAULT_WEAK_EXPOSURE_CAP_PERCENT);

        RiskAdjustedMomentumBacktestResult viaAdapterOverload = service.runWithBreadthAdapter(LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 8, 1), 1_000_000, 10, 20, "NIFTY 500", .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0,
                RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL, RiskAdjustedMomentumConstants.DEFAULT_TRAILING_STOP_PERCENT,
                RiskAdjustedMomentumConstants.DEFAULT_ATR_PERIOD, RiskAdjustedMomentumConstants.DEFAULT_ATR_MULTIPLIER,
                RiskAdjustedMomentumConstants.DEFAULT_COOLDOWN_WEEKS, RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK_SMA_PERIOD,
                RiskAdjustedMomentumConstants.DEFAULT_BREADTH_THRESHOLD_PERCENT, RiskAdjustedMomentumConstants.DEFAULT_WEAK_EXPOSURE_CAP_PERCENT,
                null, true);

        assertThat(viaAdapterOverload).isEqualTo(plain);
    }

    @Test
    void breadthAdapterOverridesEffectiveExposureRecordedInRegimeHistory() {
        StockPriceCacheService cache = fixtureCache();
        RiskAdjustedMomentumBacktestService service = new RiskAdjustedMomentumBacktestService(cache);

        BreadthExposureAdapter blockAllNewBuys = (signalDate, exposureCap, newBuysAllowed) ->
                new BreadthExposureAdapter.ExposureDecision(exposureCap, false);

        RiskAdjustedMomentumBacktestResult filtered = service.runWithBreadthAdapter(LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 8, 1), 1_000_000, 10, 20, "NIFTY 500", .1, .1, 6.5, "REPLACEMENT_ONLY", 0, 0, 0,
                RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL, RiskAdjustedMomentumConstants.DEFAULT_TRAILING_STOP_PERCENT,
                RiskAdjustedMomentumConstants.DEFAULT_ATR_PERIOD, RiskAdjustedMomentumConstants.DEFAULT_ATR_MULTIPLIER,
                RiskAdjustedMomentumConstants.DEFAULT_COOLDOWN_WEEKS, RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK_SMA_PERIOD,
                RiskAdjustedMomentumConstants.DEFAULT_BREADTH_THRESHOLD_PERCENT, RiskAdjustedMomentumConstants.DEFAULT_WEAK_EXPOSURE_CAP_PERCENT,
                blockAllNewBuys, false);

        assertThat(filtered.regimeExposureHistory()).isNotEmpty()
                .allSatisfy(point -> assertThat(point.newBuysAllowed()).isFalse());
        assertThat(filtered.parameterStability()).isEmpty();
        assertThat(filtered.walkForwardWindows()).isEmpty();
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
}
