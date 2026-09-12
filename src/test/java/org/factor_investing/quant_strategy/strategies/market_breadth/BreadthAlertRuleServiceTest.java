package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthAlertRuleServiceTest {

    @Test
    void emitsOnlyConfiguredStateTransitions() {
        BreadthDailySnapshot previous = snapshot(Map.ofEntries(
                Map.entry(BreadthMetricKeys.BREADTH_50D_PERCENT, 49.0),
                Map.entry(BreadthMetricKeys.BREADTH_200D_PERCENT, 51.0),
                Map.entry(BreadthMetricKeys.BEARISH_DIVERGENCE, 0.0),
                Map.entry(BreadthMetricKeys.ZWEIG_THRUST, 0.0),
                Map.entry(BreadthMetricKeys.MCCLELLAN_OSCILLATOR, -1.0),
                Map.entry(BreadthMetricKeys.SUMMATION_TREND, 1.0),
                Map.entry(BreadthMetricKeys.NET_NEW_HIGHS, 2.0),
                Map.entry(BreadthMetricKeys.NARROW_LEADERSHIP, 0.0),
                Map.entry(BreadthMetricKeys.INDIA_VIX_SPIKE, 0.0),
                Map.entry(BreadthMetricKeys.INDIA_VIX_ABOVE_EMA, 0.0)));
        BreadthDailySnapshot current = snapshot(Map.ofEntries(
                Map.entry(BreadthMetricKeys.BREADTH_50D_PERCENT, 51.0),
                Map.entry(BreadthMetricKeys.BREADTH_200D_PERCENT, 49.0),
                Map.entry(BreadthMetricKeys.BEARISH_DIVERGENCE, 1.0),
                Map.entry(BreadthMetricKeys.ZWEIG_THRUST, 1.0),
                Map.entry(BreadthMetricKeys.MCCLELLAN_OSCILLATOR, 2.0),
                Map.entry(BreadthMetricKeys.SUMMATION_TREND, -1.0),
                Map.entry(BreadthMetricKeys.NET_NEW_HIGHS, -1.0),
                Map.entry(BreadthMetricKeys.NARROW_LEADERSHIP, 1.0),
                Map.entry(BreadthMetricKeys.INDIA_VIX_SPIKE, 1.0),
                Map.entry(BreadthMetricKeys.INDIA_VIX_ABOVE_EMA, 1.0)));

        var types = new BreadthAlertRuleService().evaluate(previous, current).stream()
                .map(BreadthAlertCandidate::type).toList();

        assertThat(types).containsExactly(
                BreadthAlertType.BREADTH_50D_CROSS_50_UP,
                BreadthAlertType.BREADTH_200D_CROSS_DOWN,
                BreadthAlertType.BEARISH_AD_DIVERGENCE,
                BreadthAlertType.ZWEIG_BREADTH_THRUST,
                BreadthAlertType.MCCLELLAN_ZERO_CROSS_UP,
                BreadthAlertType.SUMMATION_TREND_FLIP_DOWN,
                BreadthAlertType.NET_NEW_HIGHS_NEGATIVE_FLIP,
                BreadthAlertType.SECTOR_BREADTH_NARROWING,
                BreadthAlertType.INDIA_VIX_SPIKE,
                BreadthAlertType.INDIA_VIX_CROSS_ABOVE_EMA);
    }

    @Test
    void doesNotRepeatAnAlertWhileAConditionRemainsActive() {
        BreadthDailySnapshot previous = snapshot(Map.of(BreadthMetricKeys.INDIA_VIX_SPIKE, 1.0));
        BreadthDailySnapshot current = snapshot(Map.of(BreadthMetricKeys.INDIA_VIX_SPIKE, 1.0));

        assertThat(new BreadthAlertRuleService().evaluate(previous, current)).isEmpty();
    }

    @Test
    void emitsEachCrossedFiftyDayParticipationLevel() {
        BreadthDailySnapshot previous = snapshot(Map.of(BreadthMetricKeys.BREADTH_50D_PERCENT, 49.0));
        BreadthDailySnapshot current = snapshot(Map.of(BreadthMetricKeys.BREADTH_50D_PERCENT, 71.0));

        assertThat(new BreadthAlertRuleService().evaluate(previous, current))
                .extracting(BreadthAlertCandidate::type)
                .containsExactly(BreadthAlertType.BREADTH_50D_CROSS_50_UP,
                        BreadthAlertType.BREADTH_50D_CROSS_60_UP,
                        BreadthAlertType.BREADTH_50D_CROSS_70_UP);
    }

    private BreadthDailySnapshot snapshot(Map<String, Double> values) {
        BreadthDailySnapshot snapshot = new BreadthDailySnapshot();
        snapshot.setIndicatorValues(values);
        return snapshot;
    }
}
