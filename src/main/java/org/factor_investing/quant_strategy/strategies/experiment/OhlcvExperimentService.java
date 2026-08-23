package org.factor_investing.quant_strategy.strategies.experiment;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.response.OhlcvExperimentResult;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.factor_investing.quant_strategy.repository.NSE_ETFMasterDataRepository;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
public class OhlcvExperimentService {
    private final StockPriceCacheService cacheService;
    private final NSEStockMasterDataRepository stockMasterRepository;
    private final NSE_ETFMasterDataRepository etfMasterRepository;

    public OhlcvExperimentService(StockPriceCacheService cacheService, NSEStockMasterDataRepository stockMasterRepository,
                                  NSE_ETFMasterDataRepository etfMasterRepository) {
        this.cacheService = cacheService;
        this.stockMasterRepository = stockMasterRepository;
        this.etfMasterRepository = etfMasterRepository;
    }

    public OhlcvExperimentResult runCached(AssetDataType type, LocalDate asOfDate) {
        return run(type, prices(type), asOfDate);
    }

    public OhlcvExperimentResult run(AssetDataType type, Map<String, List<OHLCV>> universe, LocalDate requestedDate) {
        if (requestedDate != null && requestedDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("As-of date cannot be in the future");
        Map<String, String> descriptions = descriptions(type);
        List<MutableRow> rows = universe.entrySet().stream()
                .map(entry -> features(entry.getKey(), descriptions.getOrDefault(entry.getKey().toUpperCase(), "OHLCV"), entry.getValue(), requestedDate))
                .filter(Objects::nonNull).toList();
        rank(rows, value -> value.ret12, (value, rank) -> value.rank12 = rank);
        rank(rows, value -> value.ret6, (value, rank) -> value.rank6 = rank);
        rank(rows, value -> value.ret3, (value, rank) -> value.rank3 = rank);
        rows.forEach(this::score);
        List<MutableRow> ordered = rows.stream().sorted(Comparator.comparingInt((MutableRow value) -> value.score).reversed()
                .thenComparingInt(value -> value.rank12 + value.rank6 + value.rank3).thenComparing(value -> value.ticker)).toList();
        List<OhlcvExperimentResult.Row> results = ordered.stream().map(MutableRow::response).toList();
        LocalDate effectiveDate = ordered.stream().map(value -> value.asOfDate).max(LocalDate::compareTo).orElse(requestedDate);
        return new OhlcvExperimentResult(type, effectiveDate, universe.size(), rows.size(), universe.size() - rows.size(), results, portfolio(ordered));
    }

    private MutableRow features(String ticker, String theme, List<OHLCV> source, LocalDate requestedDate) {
        if (source == null) return null;
        List<OHLCV> bars = source.stream().filter(Objects::nonNull)
                .filter(bar -> requestedDate == null || !DateUtil.convertDateToLocalDate(bar.getDate()).isAfter(requestedDate))
                .sorted(Comparator.comparing(OHLCV::getDate)).toList();
        int size = bars.size();
        if (size < 253) return null;
        int t = size - 1;
        OHLCV last = bars.get(t);
        if (last.getClose() <= 0) return null;
        MutableRow row = new MutableRow();
        row.ticker = ticker; row.theme = theme == null || theme.isBlank() ? ticker : theme; row.asOfDate = DateUtil.convertDateToLocalDate(last.getDate());
        row.close = last.getClose(); row.lastVolume = last.getVolume();
        row.ret12 = ratio(last.getClose(), bars.get(t - 252).getClose()); row.ret6 = ratio(last.getClose(), bars.get(t - 126).getClose());
        row.ret3 = ratio(last.getClose(), bars.get(t - 63).getClose()); row.ret1d = ratio(last.getClose(), bars.get(t - 1).getClose());
        row.sma20 = averageClose(bars, t - 19, t); row.sma50 = averageClose(bars, t - 49, t); row.sma200 = averageClose(bars, t - 199, t);
        row.sma50Prev = averageClose(bars, t - 69, t - 20);
        row.high52 = bars.subList(t - 251, t + 1).stream().mapToDouble(OHLCV::getHigh).max().orElse(0);
        row.pctFromHigh = ratio(last.getClose(), row.high52); row.atr14 = wilderAtr(bars); row.extension = row.atr14 == 0 ? Double.NaN : (last.getClose() - row.sma20) / row.atr14;
        row.vol20 = averageVolume(bars, t - 19, t); row.vol50 = averageVolume(bars, t - 49, t);
        return row.finite() ? row : null;
    }

    private void score(MutableRow row) {
        row.p1 = row.rank12 <= 10 ? 2 : row.rank12 <= 25 ? 1 : 0;
        row.p2 = row.rank6 <= 10 && row.rank3 <= 10 ? 2 : row.rank6 <= 25 && row.rank3 <= 25 ? 1 : 0;
        int spread = Math.max(row.rank12, Math.max(row.rank6, row.rank3)) - Math.min(row.rank12, Math.min(row.rank6, row.rank3));
        if (row.rank12 > 40 || (row.rank3 >= 2 * row.rank12 && row.rank3 > 20) || (row.rank6 >= row.rank12 + 12 && row.rank6 > 15)) row.p3 = 0;
        else if ((spread <= 10 && row.rank12 <= 20) || (row.rank12 <= 15 && row.rank6 <= 10 && row.rank3 <= 12)) row.p3 = 2;
        else if ((row.rank12 <= 30 && row.rank6 <= 10 && row.rank3 <= 15) || (row.rank12 <= 25 && row.rank6 <= 25 && row.rank3 <= 25)) row.p3 = 1;
        else row.p3 = 0;
        row.p4 = row.close < row.sma50 || row.pctFromHigh < -.15 ? 0 : row.close > row.sma50 && row.sma50 > row.sma200 && row.sma50 > row.sma50Prev && row.pctFromHigh >= -.08 ? 2 : 1;
        row.p5 = row.ret1d >= .08 || row.extension >= 2.5 ? 0 : row.extension < 1.5 && (row.vol20 >= row.vol50 || row.lastVolume >= 1.5 * row.vol20) ? 2 : row.extension < 2.5 ? 1 : 0;
        row.score = row.p1 + row.p2 + row.p3 + row.p4 + row.p5;
        row.bucket = row.score >= 8 ? "CORE" : row.score >= 6 ? "SATELLITE" : row.score >= 4 ? "WATCH" : "AVOID";
        row.action = !row.bucket.equals("CORE") ? row.bucket : row.p5 == 2 ? "BUY NOW" : row.p5 == 1 ? "SCALE IN" : "WAIT FOR DIP";
    }

    private List<OhlcvExperimentResult.PortfolioPosition> portfolio(List<MutableRow> ordered) {
        List<MutableRow> selected = ordered.stream().filter(value -> value.score >= 6).toList();
        double units = selected.stream().mapToDouble(value -> value.score >= 8 ? 1 : .5).sum();
        if (units == 0) return List.of();
        return selected.stream().map(value -> new OhlcvExperimentResult.PortfolioPosition(value.ticker, value.theme, value.bucket,
                (value.score >= 8 ? 1 : .5) * 100 / units, value.score)).toList();
    }

    private Map<String, List<OHLCV>> prices(AssetDataType type) { return switch (type) { case STOCK -> cacheService.getCachedAllStockPriceData(); case ETF -> cacheService.getCachedAllETFPriceData(); case INDEX -> cacheService.getCachedAllIndexPriceData(); }; }
    private Map<String, String> descriptions(AssetDataType type) {
        if (type == AssetDataType.STOCK && stockMasterRepository != null) {
            return stockMasterRepository.findAll().stream().filter(item -> item.getSymbol() != null)
                    .collect(Collectors.toMap(item -> item.getSymbol().toUpperCase(), this::stockDescription, (first, ignored) -> first));
        }
        if (type == AssetDataType.ETF && etfMasterRepository != null) {
            return etfMasterRepository.findAll().stream().filter(item -> item.getSymbol() != null)
                    .collect(Collectors.toMap(item -> item.getSymbol().toUpperCase(), item -> text(item.getUnderlying(), "Underlying unavailable"), (first, ignored) -> first));
        }
        return Map.of();
    }
    private String stockDescription(NSEStockMasterData item) {
        String company = text(item.getNameOfCompany(), item.getSymbol());
        String industry = text(item.getIndustry(), "Industry unavailable");
        return company + " · " + industry;
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private double ratio(double current, double previous) { return previous <= 0 ? Double.NaN : current / previous - 1; }
    private double averageClose(List<OHLCV> bars, int start, int end) { return bars.subList(start, end + 1).stream().mapToDouble(OHLCV::getClose).average().orElse(Double.NaN); }
    private double averageVolume(List<OHLCV> bars, int start, int end) { return bars.subList(start, end + 1).stream().mapToLong(OHLCV::getVolume).average().orElse(Double.NaN); }
    private double wilderAtr(List<OHLCV> bars) { int start = bars.size() - 253; List<Double> tr = new ArrayList<>(); for (int i = start + 1; i < bars.size(); i++) { OHLCV bar = bars.get(i); double previousClose = bars.get(i - 1).getClose(); tr.add(Math.max(bar.getHigh() - bar.getLow(), Math.max(Math.abs(bar.getHigh() - previousClose), Math.abs(bar.getLow() - previousClose)))); } if (tr.size() < 14) return Double.NaN; double atr = tr.subList(0, 14).stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN); for (int i = 14; i < tr.size(); i++) atr = (atr * 13 + tr.get(i)) / 14; return atr; }
    private void rank(List<MutableRow> rows, ToDoubleFunction<MutableRow> getter, RankSetter setter) { List<MutableRow> sorted = rows.stream().sorted(Comparator.comparingDouble(getter).reversed()).toList(); double previous = Double.NaN; int competitionRank = 0; for (int i = 0; i < sorted.size(); i++) { double value = getter.applyAsDouble(sorted.get(i)); if (i == 0 || Double.compare(value, previous) != 0) competitionRank = i + 1; setter.set(sorted.get(i), competitionRank); previous = value; } }
    @FunctionalInterface private interface RankSetter { void set(MutableRow row, int rank); }

    private static class MutableRow {
        String ticker, theme, bucket, action; LocalDate asOfDate; double close, ret12, ret6, ret3, ret1d, sma20, sma50, sma200, sma50Prev, high52, pctFromHigh, atr14, extension, vol20, vol50; long lastVolume; int rank12, rank6, rank3, p1, p2, p3, p4, p5, score;
        boolean finite() { return java.util.stream.DoubleStream.of(close, ret12, ret6, ret3, ret1d, sma20, sma50, sma200, sma50Prev, high52, pctFromHigh, atr14, extension, vol20, vol50).allMatch(Double::isFinite); }
        OhlcvExperimentResult.Row response() { return new OhlcvExperimentResult.Row(ticker, theme, asOfDate, close, ret12, ret6, ret3, ret1d, sma20, sma50, sma200, sma50Prev, high52, pctFromHigh, atr14, extension, vol20, vol50, lastVolume, rank12, rank6, rank3, p1, p2, p3, p4, p5, score, bucket, action); }
    }
}
