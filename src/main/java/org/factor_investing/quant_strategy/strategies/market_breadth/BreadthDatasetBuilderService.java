package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Builds an {@link AlignedBreadthDataset}: the trading calendar for a universe (derived from
 * that universe's own index price series — no dedicated trading-calendar table exists) plus each
 * member stock's own price series, normalized to {@code LocalDate} keys and never forward-filled.
 */
@Service
public class BreadthDatasetBuilderService {

    private static final Map<NiftyIndexName, RequiredMarketBreadthIndex> UNIVERSE_INDEX = Map.of(
            NiftyIndexName.NIFTY50, RequiredMarketBreadthIndex.NIFTY_50,
            NiftyIndexName.NIFTY500, RequiredMarketBreadthIndex.NIFTY_500,
            NiftyIndexName.NIFTY200, RequiredMarketBreadthIndex.NIFTY_200
    );

    private final BreadthUniverseService breadthUniverseService;
    private final IndexPriceDataRepository indexPriceDataRepository;
    private final StockDataRepository stockDataRepository;

    public BreadthDatasetBuilderService(BreadthUniverseService breadthUniverseService,
                                         IndexPriceDataRepository indexPriceDataRepository,
                                         StockDataRepository stockDataRepository) {
        this.breadthUniverseService = breadthUniverseService;
        this.indexPriceDataRepository = indexPriceDataRepository;
        this.stockDataRepository = stockDataRepository;
    }

    public AlignedBreadthDataset build(NiftyIndexName universe, BreadthMethodology methodology, LocalDate calculationDate) {
        List<String> members = breadthUniverseService.resolveMembers(universe, methodology, calculationDate);
        LocalDate warmUpStart = calculationDate.minusDays(
                (long) (BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS * 1.6));

        List<BreadthDataQualityIssue> sourceIssues = new ArrayList<>();
        List<LocalDate> tradingDates = deriveTradingCalendar(universe, warmUpStart, calculationDate, sourceIssues);

        Map<String, StockPricesJson> stockPricesBySymbol = new HashMap<>();
        for (StockPricesJson row : stockDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY)) {
            if (row.getNseStockMasterData() != null && row.getNseStockMasterData().getSymbol() != null) {
                stockPricesBySymbol.put(row.getNseStockMasterData().getSymbol().trim().toUpperCase(), row);
            }
        }

        Map<String, NavigableMap<LocalDate, OHLCV>> seriesBySymbol = new HashMap<>();
        for (String symbol : members) {
            StockPricesJson prices = stockPricesBySymbol.get(symbol);
            seriesBySymbol.put(symbol, normalize(symbol, prices, warmUpStart, calculationDate, sourceIssues));
        }

        return new AlignedBreadthDataset(universe, methodology, calculationDate, tradingDates, members,
                seriesBySymbol, List.copyOf(sourceIssues));
    }

    /**
     * Builds one dataset per trading date while loading the JSON price rows only once. This is
     * used by historical breadth calculations; point-in-time membership is still resolved for
     * every date, but price history is shared and consumers are required to use data no later
     * than {@link AlignedBreadthDataset#calculationDate()}.
     */
    public List<AlignedBreadthDataset> buildRange(NiftyIndexName universe, BreadthMethodology methodology,
                                                   LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Breadth calculation start date must not be after end date");
        }
        LocalDate warmUpStart = from.minusDays(
                (long) (BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS * 1.6));
        List<BreadthDataQualityIssue> sourceIssues = new ArrayList<>();
        List<LocalDate> tradingDates = deriveTradingCalendar(universe, warmUpStart, to, sourceIssues);

        Map<String, StockPricesJson> stockPricesBySymbol = new HashMap<>();
        for (StockPricesJson row : stockDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY)) {
            if (row.getNseStockMasterData() != null && row.getNseStockMasterData().getSymbol() != null) {
                stockPricesBySymbol.put(row.getNseStockMasterData().getSymbol().trim().toUpperCase(), row);
            }
        }

        Map<String, NavigableMap<LocalDate, OHLCV>> normalizedSeries = new HashMap<>();
        stockPricesBySymbol.forEach((symbol, prices) -> normalizedSeries.put(symbol,
                normalize(symbol, prices, warmUpStart, to, sourceIssues)));

        List<String> currentMembers = methodology == BreadthMethodology.CURRENT_CONSTITUENTS
                ? breadthUniverseService.resolveMembers(universe, methodology, to) : List.of();
        List<AlignedBreadthDataset> datasets = new ArrayList<>();
        for (LocalDate calculationDate : tradingDates) {
            if (calculationDate.isBefore(from)) {
                continue;
            }
            List<String> members = methodology == BreadthMethodology.CURRENT_CONSTITUENTS
                    ? currentMembers
                    : breadthUniverseService.resolveMembers(universe, methodology, calculationDate);
            Map<String, NavigableMap<LocalDate, OHLCV>> seriesBySymbol = new HashMap<>();
            for (String member : members) {
                seriesBySymbol.put(member, normalizedSeries.getOrDefault(member, new TreeMap<>()));
            }
            List<BreadthDataQualityIssue> applicableIssues = sourceIssues.stream()
                    .filter(issue -> issue.date() == null || !issue.date().isAfter(calculationDate))
                    .filter(issue -> issue.symbol() == null || members.contains(issue.symbol()))
                    .toList();
            datasets.add(new AlignedBreadthDataset(universe, methodology, calculationDate,
                    tradingDates.stream().filter(date -> !date.isAfter(calculationDate)).toList(), members,
                    seriesBySymbol, applicableIssues));
        }
        return datasets;
    }

    private List<LocalDate> deriveTradingCalendar(NiftyIndexName universe, LocalDate from, LocalDate to,
                                                   List<BreadthDataQualityIssue> sourceIssues) {
        RequiredMarketBreadthIndex requiredIndex = UNIVERSE_INDEX.get(universe);
        if (requiredIndex == null) {
            return List.of();
        }
        return indexPriceDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY).stream()
                .filter(row -> row != null && requiredIndex.matches(row.getNseIndexMasterData()))
                .findFirst()
                .map(IndexPricesJson::getOhlcvData)
                .map(ohlcvData -> normalizeTradingCalendar(ohlcvData, from, to, sourceIssues))
                .orElseGet(List::of);
    }

    private List<LocalDate> normalizeTradingCalendar(List<OHLCV> bars, LocalDate from, LocalDate to,
                                                      List<BreadthDataQualityIssue> sourceIssues) {
        HashSet<LocalDate> seen = new HashSet<>();
        List<LocalDate> dates = new ArrayList<>();
        if (bars == null) {
            return dates;
        }
        for (OHLCV bar : bars) {
            if (bar == null || bar.getDate() == null) {
                continue;
            }
            LocalDate date = DateUtil.convertDateToLocalDate(bar.getDate());
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            if (!seen.add(date)) {
                sourceIssues.add(new BreadthDataQualityIssue(null,
                        BreadthDataQualityIssue.IssueType.DUPLICATE_DATE, date,
                        "Universe index history contains more than one bar for this trading date"));
                continue;
            }
            dates.add(date);
        }
        return dates.stream().sorted().toList();
    }

    private NavigableMap<LocalDate, OHLCV> normalize(String symbol, StockPricesJson prices, LocalDate from,
                                                      LocalDate to, List<BreadthDataQualityIssue> sourceIssues) {
        TreeMap<LocalDate, OHLCV> series = new TreeMap<>();
        if (prices == null || prices.getOhlcvData() == null) {
            return series;
        }
        for (OHLCV bar : prices.getOhlcvData()) {
            if (bar == null || bar.getDate() == null) {
                continue;
            }
            LocalDate date = DateUtil.convertDateToLocalDate(bar.getDate());
            if (!date.isBefore(from) && !date.isAfter(to)) {
                if (series.containsKey(date)) {
                    sourceIssues.add(new BreadthDataQualityIssue(symbol,
                            BreadthDataQualityIssue.IssueType.DUPLICATE_DATE, date,
                            "Stock history contains more than one bar for this trading date"));
                    continue;
                }
                series.put(date, bar);
            }
        }
        return series;
    }
}
