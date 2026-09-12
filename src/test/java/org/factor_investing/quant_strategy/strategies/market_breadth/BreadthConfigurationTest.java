package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthConfigurationTest {

    @Test
    void conventionsHaveTheAgreedDefaults() {
        assertThat(BreadthCalculationConventions.DEFAULT_MOVING_AVERAGE_TYPE).isEqualTo(MovingAverageType.EMA);
        assertThat(BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS).isEqualTo(252);
        assertThat(BreadthCalculationConventions.MISSING_DATA_POLICY).isEqualTo(MissingDataPolicy.EXCLUDE_FROM_SNAPSHOT);
        assertThat(BreadthCalculationConventions.MIN_COVERAGE_VALID_PERCENT).isEqualTo(90);
        assertThat(BreadthCalculationConventions.MIN_COVERAGE_WARNING_PERCENT).isEqualTo(75);
        assertThat(BreadthCalculationConventions.MIN_SESSIONS_BEFORE_EXECUTION).isEqualTo(1);
    }

    @Test
    void scoreWeightsAndRegimeThresholdsAreInternallyConsistent() {
        assertThat(BreadthScoreDefaults.TOTAL_WEIGHT).isEqualTo(100);
        assertThat(BreadthScoreDefaults.AMBER_THRESHOLD)
                .isBetween(0.0, BreadthScoreDefaults.GREEN_THRESHOLD);
        assertThat(BreadthScoreDefaults.GREEN_THRESHOLD).isLessThanOrEqualTo(100);
        assertThat(BreadthScoreDefaults.RISING_FALLING_TREND_LOOKBACK_SESSIONS).isPositive();
        assertThat(BreadthScoreDefaults.EXPANSION_CONTRACTION_LOOKBACK_SESSIONS).isPositive();
    }
}
