package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BreadthDailyStatisticsServiceTest {
    private final BreadthDailyStatisticsService service = new BreadthDailyStatisticsService();

    @Test
    void calculatesDailyBreadthMovingAveragesHighsVolumeTrinAndBullishProxy() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate date = start.plusDays(251);
        NavigableMap<LocalDate, OHLCV> advancing = BreadthTestData.risingSeries(start, 252, 100, 1);
        NavigableMap<LocalDate, OHLCV> declining = BreadthTestData.risingSeries(start, 252, 500, -1);
        NavigableMap<LocalDate, OHLCV> unchanged = BreadthTestData.risingSeries(start, 252, 100, 0);
        advancing.put(date, BreadthTestData.bar(date, 351, 2000));
        declining.put(date, BreadthTestData.bar(date, 249, 1000));
        unchanged.put(date, BreadthTestData.bar(date, 100, 500));

        AlignedBreadthDataset dataset = new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, date, List.copyOf(advancing.keySet()),
                List.of("ADV", "DEC", "FLAT"),
                Map.of("ADV", advancing, "DEC", declining, "FLAT", unchanged), List.of());

        BreadthDailyStatistics result = service.calculate(dataset);

        assertThat(result.advancers()).isEqualTo(1);
        assertThat(result.decliners()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.adRatio()).isEqualTo(1);
        assertThat(result.advanceProportion()).isEqualTo(0.5);
        assertThat(result.advancingVolume()).isEqualTo(2000);
        assertThat(result.decliningVolume()).isEqualTo(1000);
        assertThat(result.upDownVolumeRatio()).isEqualTo(2);
        assertThat(result.trin()).isEqualTo(0.5);
        assertThat(result.newHighs()).isEqualTo(1);
        assertThat(result.newLows()).isEqualTo(1);
        assertThat(result.breadth50Percent()).isCloseTo(100.0 / 3, within(0.0001));
        assertThat(result.breadth200Percent()).isCloseTo(100.0 / 3, within(0.0001));
        assertThat(result.bullishPercentProxy()).isCloseTo(100.0 / 3, within(0.0001));
    }
}
