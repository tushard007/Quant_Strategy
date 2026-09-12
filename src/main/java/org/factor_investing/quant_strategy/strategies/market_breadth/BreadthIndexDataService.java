package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class BreadthIndexDataService {
    private static final Map<NiftyIndexName, RequiredMarketBreadthIndex> BENCHMARK_INDICES = Map.of(
            NiftyIndexName.NIFTY50, RequiredMarketBreadthIndex.NIFTY_50,
            NiftyIndexName.NIFTY500, RequiredMarketBreadthIndex.NIFTY_500,
            NiftyIndexName.NIFTY200, RequiredMarketBreadthIndex.NIFTY_200
    );

    private final IndexPriceDataRepository indexPriceDataRepository;

    public BreadthIndexDataService(IndexPriceDataRepository indexPriceDataRepository) {
        this.indexPriceDataRepository = indexPriceDataRepository;
    }

    public BreadthIndexSeries load(NiftyIndexName universe) {
        RequiredMarketBreadthIndex benchmarkIndex = BENCHMARK_INDICES.get(universe);
        if (benchmarkIndex == null) {
            throw new IllegalArgumentException("Market breadth supports NIFTY50, NIFTY200, or NIFTY500");
        }
        List<IndexPricesJson> dailyPrices = indexPriceDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY);
        NavigableMap<LocalDate, OHLCV> benchmark = loadIndex(benchmarkIndex, dailyPrices);
        Map<RequiredMarketBreadthIndex, NavigableMap<LocalDate, OHLCV>> sectors =
                new EnumMap<>(RequiredMarketBreadthIndex.class);
        for (RequiredMarketBreadthIndex index : RequiredMarketBreadthIndex.sectorIndices()) {
            sectors.put(index, loadIndex(index, dailyPrices));
        }
        return new BreadthIndexSeries(benchmark, sectors,
                loadIndex(RequiredMarketBreadthIndex.INDIA_VIX, dailyPrices));
    }

    private NavigableMap<LocalDate, OHLCV> loadIndex(RequiredMarketBreadthIndex requiredIndex,
                                                     List<IndexPricesJson> dailyPrices) {
        return dailyPrices.stream()
                .filter(row -> row != null && requiredIndex.matches(row.getNseIndexMasterData()))
                .findFirst()
                .map(IndexPricesJson::getOhlcvData)
                .map(bars -> {
                    TreeMap<LocalDate, OHLCV> result = new TreeMap<>();
                    if (bars != null) {
                        bars.stream().filter(bar -> bar != null && bar.getDate() != null)
                                .forEach(bar -> result.put(DateUtil.convertDateToLocalDate(bar.getDate()), bar));
                    }
                    return (NavigableMap<LocalDate, OHLCV>) result;
                }).orElseGet(TreeMap::new);
    }
}
