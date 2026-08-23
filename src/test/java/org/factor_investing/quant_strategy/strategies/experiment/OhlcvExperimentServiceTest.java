package org.factor_investing.quant_strategy.strategies.experiment;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.response.OhlcvExperimentResult;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcvExperimentServiceTest {
    @Test
    void computesFeaturesAndUsesCompetitionRanksForTies() {
        List<OHLCV> identical = bars(100, 1);
        OhlcvExperimentService service = new OhlcvExperimentService(null);

        OhlcvExperimentResult result = service.run(AssetDataType.ETF, Map.of("AAA", identical, "BBB", bars(100, 1)), LocalDate.of(2026, 8, 23));

        assertThat(result.universeSize()).isEqualTo(2);
        assertThat(result.scoredCount()).isEqualTo(2);
        assertThat(result.results()).allSatisfy(row -> {
            assertThat(row.rank12()).isEqualTo(1);
            assertThat(row.rank6()).isEqualTo(1);
            assertThat(row.rank3()).isEqualTo(1);
            assertThat(row.score()).isBetween(0, 10);
        });
    }

    @Test
    void skipsTickersWithout253Bars() {
        OhlcvExperimentResult result = new OhlcvExperimentService(null).run(AssetDataType.ETF, Map.of("SHORT", bars(100, 0).subList(0, 252)), LocalDate.of(2026, 8, 23));

        assertThat(result.scoredCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
    }

    private List<OHLCV> bars(double start, double dailyStep) {
        List<OHLCV> values = new ArrayList<>(); LocalDate date = LocalDate.of(2025, 1, 1);
        for (int index = 0; index < 253; index++) { double close = start + index * dailyStep; values.add(new OHLCV(Date.valueOf(date.plusDays(index)), close - .5, close + 1, close - 1, close, 1000 + index)); }
        return values;
    }
}
