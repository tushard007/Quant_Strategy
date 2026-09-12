package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMetricKeys.*;

class MarketBreadthCalculationServiceTest {

    @Test
    void calculatesRecursiveIndicatorsThrustSectorParticipationDivergenceAndVix() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        int historicalCount = 260;
        int calculationCount = 30;
        Map<String, NavigableMap<LocalDate, OHLCV>> stocks = new HashMap<>();
        for (String symbol : List.of("AAA", "BBB", "CCC", "DDD")) {
            NavigableMap<LocalDate, OHLCV> series = BreadthTestData.risingSeries(start, historicalCount, 100, 1);
            double close = series.lastEntry().getValue().getClose();
            for (int index = 0; index < calculationCount; index++) {
                close += index < 10 ? -1 : 3;
                LocalDate date = start.plusDays(historicalCount + index);
                series.put(date, BreadthTestData.bar(date, close, 1000));
            }
            stocks.put(symbol, series);
        }

        List<AlignedBreadthDataset> datasets = new ArrayList<>();
        List<LocalDate> allDates = List.copyOf(stocks.get("AAA").keySet());
        for (int index = 0; index < calculationCount; index++) {
            LocalDate date = start.plusDays(historicalCount + index);
            datasets.add(new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                    BreadthMethodology.CURRENT_CONSTITUENTS, date,
                    allDates.stream().filter(item -> !item.isAfter(date)).toList(),
                    List.copyOf(stocks.keySet()), stocks, List.of()));
        }

        NavigableMap<LocalDate, OHLCV> benchmark = BreadthTestData.risingSeries(start,
                historicalCount + calculationCount, 1000, 2);
        Map<RequiredMarketBreadthIndex, NavigableMap<LocalDate, OHLCV>> sectors =
                new EnumMap<>(RequiredMarketBreadthIndex.class);
        RequiredMarketBreadthIndex.sectorIndices().forEach(sector -> sectors.put(sector,
                BreadthTestData.risingSeries(start, historicalCount + calculationCount, 100, 1)));
        NavigableMap<LocalDate, OHLCV> vix = BreadthTestData.risingSeries(start,
                historicalCount + calculationCount, 15, 0);
        LocalDate finalDate = start.plusDays(historicalCount + calculationCount - 1);
        vix.put(finalDate, BreadthTestData.bar(finalDate, 18, 0));

        BreadthDataQualityService qualityService = new BreadthDataQualityService();
        MarketBreadthCalculationService service = new MarketBreadthCalculationService(
                new BreadthDailyStatisticsService(), new BreadthCoverageService(qualityService), qualityService,
                new BreadthScoreService());
        List<BreadthCalculationResult> results = service.calculate(datasets,
                new BreadthIndexSeries(benchmark, sectors, vix), BreadthScoreParameters.defaults(),
                BreadthCalculationSeed.empty());

        assertThat(results.getFirst().indicatorValues().get(ADVANCERS)).isZero();
        assertThat(results.getFirst().indicatorValues().get(DECLINERS)).isEqualTo(4);
        assertThat(results.getFirst().indicatorValues().get(AD_LINE)).isEqualTo(-4);
        assertThat(results).anyMatch(result -> result.indicatorValues().get(MCCLELLAN_ZERO_CROSS) == 1);
        assertThat(results).anyMatch(result -> result.indicatorValues().get(MCCLELLAN_OSCILLATOR) > 0);
        assertThat(results).anyMatch(result -> result.indicatorValues().get(ZWEIG_THRUST) == 1);
        assertThat(results).anyMatch(result -> result.indicatorValues().get(BEARISH_DIVERGENCE) == 1);
        BreadthCalculationResult last = results.getLast();
        assertThat(last.indicatorValues().get(SECTOR_PARTICIPATION)).isEqualTo(11);
        assertThat(last.indicatorValues().get(INDIA_VIX_SPIKE)).isEqualTo(1);
        assertThat(last.indicatorValues().get(NET_NEW_HIGHS)).isPositive();
        assertThat(last.score().componentReasons()).isNotEmpty();
        assertThat(last.score().regime()).isNotEqualTo(BreadthRegime.GREEN);
    }
}
