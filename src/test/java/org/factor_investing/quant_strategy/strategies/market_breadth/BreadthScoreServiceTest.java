package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMetricKeys.*;

class BreadthScoreServiceTest {
    private final BreadthScoreService service = new BreadthScoreService();

    @Test
    void scoresStrongBreadthGreenAndReturnsExplanations() {
        Map<String, Double> metrics = strongMetrics();

        BreadthScoreResult result = service.score(metrics, BreadthSnapshotQuality.VALID,
                BreadthScoreParameters.defaults());

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.regime()).isEqualTo(BreadthRegime.GREEN);
        assertThat(result.componentScores()).hasSize(11);
        assertThat(result.componentReasons()).containsKeys("BREADTH_50D", "PRICE_BREADTH_CONFIRMATION");
    }

    @Test
    void appliesQualityAndVixCriticalOverrides() {
        Map<String, Double> metrics = strongMetrics();
        metrics.put(INDIA_VIX_SPIKE, 1.0);
        assertThat(service.score(metrics, BreadthSnapshotQuality.VALID, BreadthScoreParameters.defaults()).regime())
                .isEqualTo(BreadthRegime.AMBER);
        assertThat(service.score(metrics, BreadthSnapshotQuality.INVALID, BreadthScoreParameters.defaults()).regime())
                .isEqualTo(BreadthRegime.RED);
    }

    private Map<String, Double> strongMetrics() {
        Map<String, Double> values = new HashMap<>();
        values.put(AD_LINE_RISING, 1.0);
        values.put(BREADTH_50D_PERCENT, 70.0);
        values.put(BREADTH_50D_RISING, 1.0);
        values.put(BREADTH_200D_PERCENT, 60.0);
        values.put(MCCLELLAN_OSCILLATOR, 10.0);
        values.put(SUMMATION_TREND, 1.0);
        values.put(NET_NEW_HIGHS, 5.0);
        values.put(NET_NEW_HIGHS_EXPANDING_5D, 1.0);
        values.put(ZWEIG_THRUST, 1.0);
        values.put(ZWEIG_RATIO_10D, 0.7);
        values.put(TRIN, 0.8);
        values.put(BULLISH_PERCENT_PROXY, 60.0);
        values.put(SECTOR_PARTICIPATION, 8.0);
        values.put(BULLISH_CONFIRMATION, 1.0);
        return values;
    }
}
