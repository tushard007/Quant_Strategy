package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Parallel, independent counterpart to MomentumBacktestService. Differs from the original in:
 * 1) 12-1 momentum signal (skips the most recent month) instead of raw 12-month return,
 * 2) inverse-volatility position sizing instead of equal-weight,
 * 3) a trailing-stop + market-regime overlay wired into the monthly loop by default (tunable, not optional),
 * 4) Sharpe/volatility use monthly returns annualized by sqrt(12), consistent throughout (no daily/sqrt(252) mixing).
 */
@Service
public class RiskAdjustedMomentumBacktestService {
    private final StockPriceCacheService cacheService;

    public RiskAdjustedMomentumBacktestService(StockPriceCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public RiskAdjustedMomentumBacktestResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode) {
        return run(startDate, endDate, initialCapital, entryRank, retentionRank, benchmark, transactionCostPercent,
                slippagePercent, riskFreeRatePercent, rebalanceMode, 0, 0, 0,
                RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL, RiskAdjustedMomentumConstants.DEFAULT_TRAILING_STOP_PERCENT,
                RiskAdjustedMomentumConstants.DEFAULT_ATR_PERIOD, RiskAdjustedMomentumConstants.DEFAULT_ATR_MULTIPLIER,
                RiskAdjustedMomentumConstants.DEFAULT_COOLDOWN_WEEKS, RiskAdjustedMomentumConstants.DEFAULT_BENCHMARK_SMA_PERIOD,
                RiskAdjustedMomentumConstants.DEFAULT_BREADTH_THRESHOLD_PERCENT, RiskAdjustedMomentumConstants.DEFAULT_WEAK_EXPOSURE_CAP_PERCENT);
    }

    public RiskAdjustedMomentumBacktestResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent,
                                      String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier,
                                      int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent,
                                      double weakExposureCapPercent) {
        RiskAdjustedMomentumBacktestResult base = runCore(startDate, endDate, initialCapital, entryRank, retentionRank,
                benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, null);
        return addDiagnostics(base, entryRank, retentionRank, benchmark, transactionCostPercent,
                slippagePercent, riskFreeRatePercent, rebalanceMode, bufferAmount,
                maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, null, true);
    }

    /**
     * Same as {@link #run(LocalDate, LocalDate, double, int, int, String, double, double, double, String, double, double,
     * double, String, double, int, double, int, int, double, double)}, but allows a caller (e.g. a breadth-filtered
     * backtest) to override the per-period exposure cap / new-buy gate via {@code breadthExposureAdapter}. Passing
     * {@code null} reproduces the plain {@code run(...)} behavior exactly.
     */
    public RiskAdjustedMomentumBacktestResult runWithBreadthAdapter(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent,
                                      String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier,
                                      int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent,
                                      double weakExposureCapPercent, BreadthExposureAdapter breadthExposureAdapter,
                                      boolean includeStabilityAndWalkForward) {
        RiskAdjustedMomentumBacktestResult base = runCore(startDate, endDate, initialCapital, entryRank, retentionRank,
                benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter);
        return addDiagnostics(base, entryRank, retentionRank, benchmark, transactionCostPercent,
                slippagePercent, riskFreeRatePercent, rebalanceMode, bufferAmount,
                maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter, includeStabilityAndWalkForward);
    }

    RiskAdjustedMomentumBacktestResult runCore(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent,
                                      String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier,
                                      int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent,
                                      double weakExposureCapPercent, BreadthExposureAdapter breadthExposureAdapter) {
        validate(startDate, endDate, initialCapital, entryRank, retentionRank, transactionCostPercent, slippagePercent);
        if (riskFreeRatePercent < 0 || riskFreeRatePercent > 100)
            throw new IllegalArgumentException("Risk-free rate must be between 0 and 100 percent");
        boolean equalWeightMonthly = "EQUAL_WEIGHT".equalsIgnoreCase(rebalanceMode);
        if (!equalWeightMonthly && !"REPLACEMENT_ONLY".equalsIgnoreCase(rebalanceMode))
            throw new IllegalArgumentException("Rebalance mode must be REPLACEMENT_ONLY or EQUAL_WEIGHT");
        if (bufferAmount < 0 || maximumLeverageAmount < 0 || borrowingInterestRatePercent < 0)
            throw new IllegalArgumentException("Buffer, leverage and borrowing interest cannot be negative");
        String normalizedStopModel = stopModel == null ? RiskAdjustedMomentumConstants.DEFAULT_STOP_MODEL : stopModel.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FIXED", "TIERED", "ATR").contains(normalizedStopModel))
            throw new IllegalArgumentException("Stop model must be FIXED, TIERED or ATR");

        Map<String, NavigableMap<LocalDate, OHLCV>> stocks = normalize(cacheService.getCachedAllStockPriceData());
        Map<String, NavigableMap<LocalDate, OHLCV>> indexes = normalize(cacheService.getCachedAllIndexPriceData());
        if (stocks.isEmpty()) throw new IllegalArgumentException("No cached stock price data is available");
        NavigableMap<LocalDate, OHLCV> benchmarkPrices = resolveBenchmark(indexes, benchmark);
        List<LocalDate> signals = monthlySignals(stocks, startDate, endDate);
        if (signals.size() < 2) throw new IllegalArgumentException("The selected range needs at least two monthly signal dates");

        double value = initialCapital, peak = initialCapital, totalCosts = 0;
        double cashBalance = initialCapital + bufferAmount, maximumBufferUsed = 0, maximumBorrowed = 0,
                borrowingInterestPaid = 0, lowestCashBalance = initialCapital;
        Map<String, Holding> holdings = new LinkedHashMap<>();
        Map<String, LocalDate> cooldown = new HashMap<>();
        List<RiskAdjustedMomentumBacktestResult.Rebalance> rebalances = new ArrayList<>();
        List<RiskAdjustedMomentumBacktestResult.EquityPoint> curve = new ArrayList<>();
        List<RiskAdjustedMomentumBacktestResult.StopEvent> stopEvents = new ArrayList<>();
        List<RiskAdjustedMomentumBacktestResult.RegimePoint> regimePoints = new ArrayList<>();
        LocalDate firstExecution = nextSession(benchmarkPrices, signals.getFirst());
        if (firstExecution == null) throw new IllegalArgumentException("Benchmark has no execution price after the first signal date");
        if (firstExecution.isAfter(startDate.plusDays(10)))
            throw new IllegalArgumentException("Benchmark " + benchmark + " does not cover the backtest start date "
                    + startDate + "; first available execution date is " + firstExecution);
        double benchmarkEntry = benchmarkPrices.get(firstExecution).getOpen();
        curve.add(new RiskAdjustedMomentumBacktestResult.EquityPoint(firstExecution, initialCapital, initialCapital, 0));

        for (int period = 0; period < signals.size() - 1; period++) {
            LocalDate signalDate = signals.get(period), nextSignal = signals.get(period + 1);
            List<RankedStock> ranks = rank(stocks, signalDate);
            if (ranks.isEmpty()) continue;
            Map<String, RankedStock> byTicker = ranks.stream().collect(Collectors.toMap(RankedStock::ticker, value1 -> value1));

            // --- Stop-loss overlay: force-exit any holding that has breached its trailing stop as of this signal ---
            List<String> stoppedOut = new ArrayList<>();
            for (String ticker : new ArrayList<>(holdings.keySet())) {
                Holding holding = holdings.get(ticker);
                OHLCV bar = barAtOrBefore(stocks.get(ticker), signalDate);
                if (bar == null) continue;
                holding.peak = Math.max(holding.peak, bar.getClose());
                double level = stopLevel(normalizedStopModel, holding, stocks.get(ticker), signalDate,
                        trailingStopPercent, atrPeriod, atrMultiplier);
                if (bar.getClose() <= level) {
                    OHLCV exitBar = barAtOrAfter(stocks.get(ticker), signalDate);
                    if (exitBar == null) continue;
                    holdings.remove(ticker);
                    double exitPrice = exitBar.getOpen() * (1 - slippagePercent / 100.0);
                    double proceeds = holding.quantity * exitPrice * (1 - transactionCostPercent / 100.0);
                    cashBalance += proceeds;
                    double pnl = proceeds - holding.invested;
                    totalCosts += holding.quantity * exitPrice * transactionCostPercent / 100.0
                            + holding.quantity * exitBar.getOpen() * slippagePercent / 100.0;
                    LocalDate eligible = signalDate.plusWeeks(cooldownWeeks);
                    cooldown.put(ticker, eligible);
                    stopEvents.add(new RiskAdjustedMomentumBacktestResult.StopEvent(ticker, holding.entryDate, holding.entryPrice,
                            holding.peak, level, signalDate, signalDate, exitPrice, holding.quantity, pnl, eligible));
                    stoppedOut.add(ticker);
                }
            }

            // --- Market-regime gate: benchmark trend + universe breadth caps how many new positions may open ---
            double breadth = breadth(stocks, signalDate, benchmarkSmaPeriod);
            boolean benchmarkAboveSma = aboveSma(benchmarkPrices, signalDate, benchmarkSmaPeriod);
            double exposureCap = breadth < breadthThresholdPercent ? weakExposureCapPercent : 100;
            boolean newBuysAllowed = benchmarkAboveSma;
            if (breadthExposureAdapter != null) {
                BreadthExposureAdapter.ExposureDecision override = breadthExposureAdapter.adjust(signalDate, exposureCap, newBuysAllowed);
                exposureCap = override.exposureCapPercent();
                newBuysAllowed = override.newBuysAllowed();
            }
            regimePoints.add(new RiskAdjustedMomentumBacktestResult.RegimePoint(signalDate, breadth, benchmarkAboveSma, exposureCap, newBuysAllowed));
            int effectiveEntryRank = Math.max(0, (int) Math.floor(entryRank * exposureCap / 100.0));

            List<String> retained = holdings.keySet().stream().filter(ticker -> {
                RankedStock row = byTicker.get(ticker);
                return row != null && row.totalRankPosition <= retentionRank;
            }).toList();
            LinkedHashSet<String> selected = new LinkedHashSet<>(retained);
            if (newBuysAllowed) {
                ranks.stream().filter(row -> row.totalRankPosition <= entryRank)
                        .map(RankedStock::ticker)
                        .filter(ticker -> tradableInPeriod(stocks.get(ticker), signalDate, nextSignal))
                        .filter(ticker -> !selected.contains(ticker))
                        .filter(ticker -> {
                            LocalDate eligible = cooldown.get(ticker);
                            return eligible == null || !signalDate.isBefore(eligible);
                        })
                        .limit(Math.max(0, effectiveEntryRank - selected.size())).forEach(selected::add);
            }

            Set<String> executionTickers = new HashSet<>(selected); executionTickers.addAll(holdings.keySet());
            LocalDate executionDate = executionTickers.stream().map(stocks::get).filter(Objects::nonNull)
                    .map(series -> series.higherKey(signalDate)).filter(Objects::nonNull)
                    .filter(date -> !date.isAfter(nextSignal)).max(LocalDate::compareTo).orElse(signalDate);
            List<RiskAdjustedMomentumBacktestResult.Decision> decisions = new ArrayList<>();
            double costsBeforeRebalance = totalCosts;
            double tradedValue = 0;
            for (String ticker : new ArrayList<>(holdings.keySet())) {
                if (selected.contains(ticker)) continue;
                Holding old = holdings.get(ticker); RankedStock current = byTicker.get(ticker);
                OHLCV bar = barAtOrAfter(stocks.get(ticker), executionDate);
                if (bar == null) continue;
                holdings.remove(ticker);
                double exitPrice = bar.getOpen() * (1 - slippagePercent / 100.0);
                double proceeds = old.quantity * exitPrice * (1 - transactionCostPercent / 100.0);
                cashBalance += proceeds;
                double pnl = proceeds - old.invested;
                totalCosts += old.quantity * exitPrice * transactionCostPercent / 100.0
                        + old.quantity * bar.getOpen() * slippagePercent / 100.0;
                tradedValue += old.quantity * exitPrice;
                decisions.add(decision(ticker, "SELL", old.rank, current, old.entryDate, exitPrice, old.quantity, pnl));
            }

            // Inverse-volatility target weights across the selected slots, normalized to sum to 1.
            Map<String, Double> slotWeights = inverseVolWeights(selected, byTicker, entryRank);
            for (String ticker : selected) {
                RankedStock current = byTicker.get(ticker); Holding old = holdings.get(ticker);
                double slotWeight = slotWeights.getOrDefault(ticker, 1.0 / Math.max(1, entryRank));
                if (old != null) {
                    OHLCV resizeBar = barAfter(stocks.get(ticker), signalDate);
                    double rawResizePrice = resizeBar == null ? old.entryPrice : resizeBar.getOpen();
                    double targetAllocation = equalWeightMonthly ? value * slotWeight : initialCapital * slotWeight;
                    long targetQuantity = equalWeightMonthly && rawResizePrice > 0
                            ? (long) Math.floor(targetAllocation / (rawResizePrice * (1 + slippagePercent / 100.0) * (1 + transactionCostPercent / 100.0)))
                            : old.quantity;
                    long difference = targetQuantity - old.quantity;
                    if (difference > 0) {
                        double resizePrice = rawResizePrice * (1 + slippagePercent / 100.0);
                        long affordableQuantity = (long) Math.floor(Math.max(0, cashBalance + maximumLeverageAmount) / (resizePrice * (1 + transactionCostPercent / 100.0)));
                        difference = Math.min(difference, affordableQuantity); targetQuantity = old.quantity + difference;
                        double addedInvestment = difference * resizePrice * (1 + transactionCostPercent / 100.0);
                        totalCosts += difference * resizePrice * transactionCostPercent / 100.0
                                + difference * rawResizePrice * slippagePercent / 100.0;
                        tradedValue += difference * resizePrice;
                        cashBalance -= addedInvestment; old.invested += addedInvestment; old.quantity = targetQuantity;
                        old.entryPrice = old.invested / old.quantity;
                        decisions.add(decision(ticker, "RESIZE_UP", old.rank, current, old.entryDate, resizePrice, difference, null));
                    } else if (difference < 0) {
                        long soldQuantity = -difference;
                        double resizePrice = rawResizePrice * (1 - slippagePercent / 100.0);
                        double costBasisSold = old.invested * soldQuantity / old.quantity;
                        double proceeds = soldQuantity * resizePrice * (1 - transactionCostPercent / 100.0);
                        double realizedPnl = proceeds - costBasisSold;
                        totalCosts += soldQuantity * resizePrice * transactionCostPercent / 100.0
                                + soldQuantity * rawResizePrice * slippagePercent / 100.0;
                        tradedValue += soldQuantity * resizePrice;
                        cashBalance += proceeds; old.invested -= costBasisSold; old.quantity = targetQuantity;
                        decisions.add(decision(ticker, "RESIZE_DOWN", old.rank, current, old.entryDate, resizePrice, soldQuantity, realizedPnl));
                    } else decisions.add(decision(ticker, "KEEP", old.rank, current, old.entryDate, old.entryPrice, old.quantity, null));
                    old.rank = current.totalRankPosition; continue;
                }
                LocalDate entryDate = barDateAfter(stocks.get(ticker), signalDate);
                if (entryDate == null || entryDate.isAfter(nextSignal)) continue;
                OHLCV bar = stocks.get(ticker).get(entryDate); if (bar == null || bar.getOpen() <= 0) continue;
                double entryPrice = bar.getOpen() * (1 + slippagePercent / 100.0);
                double targetAllocation = equalWeightMonthly ? value * slotWeight : initialCapital * slotWeight;
                double affordableAllocation = Math.max(0, cashBalance + maximumLeverageAmount);
                long quantity = (long) Math.floor(Math.min(targetAllocation, affordableAllocation) / (entryPrice * (1 + transactionCostPercent / 100.0)));
                if (quantity < 1) continue;
                double invested = quantity * entryPrice * (1 + transactionCostPercent / 100.0);
                cashBalance -= invested;
                totalCosts += quantity * entryPrice * transactionCostPercent / 100.0
                        + quantity * bar.getOpen() * slippagePercent / 100.0;
                tradedValue += quantity * entryPrice;
                Holding holding = new Holding(current.totalRankPosition, entryDate, entryPrice, quantity, invested);
                holding.peak = entryPrice;
                holdings.put(ticker, holding);
                decisions.add(decision(ticker, "BUY", null, current, entryDate, entryPrice, quantity, null));
            }
            double turnover = value <= 0 ? 0 : tradedValue / value;
            double costs = totalCosts - costsBeforeRebalance;
            double periodBorrowingInterest = cashBalance < 0 ? Math.abs(cashBalance) * borrowingInterestRatePercent / 100.0 / 12.0 : 0;
            cashBalance -= periodBorrowingInterest; borrowingInterestPaid += periodBorrowingInterest;
            maximumBorrowed = Math.max(maximumBorrowed, Math.max(0, -cashBalance));
            maximumBufferUsed = Math.max(maximumBufferUsed, Math.min(bufferAmount, Math.max(0, bufferAmount - Math.max(0, cashBalance))));
            double netCash = cashBalance - bufferAmount;
            lowestCashBalance = Math.min(lowestCashBalance, netCash);
            LocalDate valuationDate = nextSignal;
            value = portfolioValue(holdings, stocks, valuationDate, cashBalance, bufferAmount);
            peak = Math.max(peak, value);
            Map.Entry<LocalDate, OHLCV> benchmarkValuation = benchmarkPrices.floorEntry(valuationDate);
            if (benchmarkValuation == null)
                throw new IllegalArgumentException("Benchmark " + benchmark + " has no value on or before " + valuationDate);
            double benchmarkValue = initialCapital * benchmarkValuation.getValue().getClose() / benchmarkEntry;
            curve.add(new RiskAdjustedMomentumBacktestResult.EquityPoint(valuationDate, value, benchmarkValue, value / peak - 1));
            rebalances.add(new RiskAdjustedMomentumBacktestResult.Rebalance(signalDate, executionDate, value, benchmarkValue,
                    netCash, turnover * 100, costs, decisions));
        }
        double totalReturn = value / initialCapital - 1;
        double years = Math.max(1.0 / 365.25, ChronoUnit.DAYS.between(startDate, endDate) / 365.25);
        double benchmarkFinal = curve.getLast().benchmarkValue();
        double benchmarkReturn = benchmarkFinal / initialCapital - 1;
        double portfolioCagr = Math.pow(value / initialCapital, 1 / years) - 1;
        double benchmarkCagr = Math.pow(benchmarkFinal / initialCapital, 1 / years) - 1;
        List<Double> portfolioMonthlyReturns = periodReturns(curve, RiskAdjustedMomentumBacktestResult.EquityPoint::portfolioValue);
        List<Double> benchmarkMonthlyReturns = periodReturns(curve, RiskAdjustedMomentumBacktestResult.EquityPoint::benchmarkValue);
        double annualizedVolatility = standardDeviation(portfolioMonthlyReturns) * Math.sqrt(12);
        double riskFreeRate = riskFreeRatePercent / 100.0;
        double monthlyRiskFreeRate = Math.pow(1 + riskFreeRate, 1.0 / 12) - 1;
        double downsideDeviation = Math.sqrt(portfolioMonthlyReturns.stream()
                .mapToDouble(item -> Math.pow(Math.min(0, item - monthlyRiskFreeRate), 2)).average().orElse(0)) * Math.sqrt(12);
        double benchmarkMaximumDrawdown = maximumDrawdown(curve.stream().map(RiskAdjustedMomentumBacktestResult.EquityPoint::benchmarkValue).toList());
        double sharpeRatio = annualizedVolatility == 0 ? 0 : (portfolioCagr - riskFreeRate) / annualizedVolatility;
        double sortinoRatio = downsideDeviation == 0 ? 0 : (portfolioCagr - riskFreeRate) / downsideDeviation;
        double maxDrawdown = curve.stream().mapToDouble(RiskAdjustedMomentumBacktestResult.EquityPoint::drawdown).min().orElse(0);
        double calmarRatio = maxDrawdown == 0 ? 0 : portfolioCagr / Math.abs(maxDrawdown);
        double monthlyWinRate = portfolioMonthlyReturns.isEmpty() ? 0 : portfolioMonthlyReturns.stream().filter(item -> item > 0).count() * 100.0 / portfolioMonthlyReturns.size();
        int comparablePeriods = Math.min(portfolioMonthlyReturns.size(), benchmarkMonthlyReturns.size());
        long outperformingPeriods = java.util.stream.IntStream.range(0, comparablePeriods)
                .filter(index -> portfolioMonthlyReturns.get(index) > benchmarkMonthlyReturns.get(index)).count();
        double benchmarkOutperformanceRate = comparablePeriods == 0 ? 0 : outperformingPeriods * 100.0 / comparablePeriods;
        double finalPortfolioValue = value;
        List<RiskAdjustedMomentumBacktestResult.Position> positions = holdings.entrySet().stream().map(entry -> {
            OHLCV bar = barAtOrBefore(stocks.get(entry.getKey()), endDate); Holding holding = entry.getValue();
            double current = bar == null ? holding.entryPrice : bar.getClose(), market = current * holding.quantity;
            return new RiskAdjustedMomentumBacktestResult.Position(entry.getKey(), holding.rank, holding.quantity, holding.entryPrice,
                    current, market, market - holding.invested, finalPortfolioValue == 0 ? 0 : market / finalPortfolioValue * 100);
        }).toList();
        return new RiskAdjustedMomentumBacktestResult(startDate, endDate, initialCapital, value, totalReturn,
                portfolioCagr, maxDrawdown,
                benchmark, benchmarkFinal, benchmarkReturn, benchmarkCagr,
                benchmarkMaximumDrawdown, annualizedVolatility, sharpeRatio, sortinoRatio, calmarRatio,
                monthlyWinRate, benchmarkOutperformanceRate, riskFreeRatePercent,
                equalWeightMonthly ? "EQUAL_WEIGHT" : "REPLACEMENT_ONLY",
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, maximumBufferUsed,
                maximumBorrowed, borrowingInterestPaid, lowestCashBalance,
                totalReturn - benchmarkReturn, rebalances.size(),
                rebalances.stream().mapToInt(item -> (int) item.decisions().stream().filter(d -> !"KEEP".equals(d.action())).count()).sum(),
                totalCosts, normalizedStopModel, trailingStopPercent, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent,
                curve, positions, rebalances,
                List.of(), List.of(), List.of(), List.of(), List.of(), stopEvents, regimePoints);
    }

    private RiskAdjustedMomentumBacktestResult addDiagnostics(RiskAdjustedMomentumBacktestResult base, int entryRank, int retentionRank,
            String benchmark, double transactionCostPercent, double slippagePercent,
            double riskFreeRatePercent, String rebalanceMode, double bufferAmount,
            double maximumLeverageAmount, double borrowingInterestRatePercent,
            String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier,
            int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent, double weakExposureCapPercent,
            BreadthExposureAdapter breadthExposureAdapter, boolean includeStabilityAndWalkForward) {
        List<RiskAdjustedMomentumBacktestResult.ParameterStability> stability = includeStabilityAndWalkForward
                ? parameterStability(base, entryRank, retentionRank, benchmark, transactionCostPercent, slippagePercent,
                riskFreeRatePercent, rebalanceMode, bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter) : List.of();
        List<RiskAdjustedMomentumBacktestResult.WalkForwardWindow> walkForward = includeStabilityAndWalkForward
                ? walkForward(base, stability, benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent,
                rebalanceMode, bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter) : List.of();
        return new RiskAdjustedMomentumBacktestResult(base.startDate(), base.endDate(), base.initialCapital(), base.finalValue(),
                base.totalReturn(), base.cagr(), base.maximumDrawdown(), base.benchmark(),
                base.benchmarkFinalValue(), base.benchmarkReturn(), base.benchmarkCagr(),
                base.benchmarkMaximumDrawdown(), base.annualizedVolatility(), base.sharpeRatio(),
                base.sortinoRatio(), base.calmarRatio(), base.monthlyWinRate(),
                base.benchmarkOutperformanceRate(), base.riskFreeRatePercent(), base.rebalanceMode(),
                base.bufferAmount(), base.maximumLeverageAmount(), base.borrowingInterestRatePercent(),
                base.maximumBufferUsed(), base.maximumBorrowed(), base.borrowingInterestPaid(),
                base.lowestCashBalance(), base.excessReturn(), base.rebalanceCount(), base.tradeCount(),
                base.totalCosts(), base.stopModel(), base.trailingStopPercent(), base.cooldownWeeks(),
                base.benchmarkSmaPeriod(), base.breadthThresholdPercent(), base.weakExposureCapPercent(),
                base.equityCurve(), base.finalPositions(), base.rebalances(),
                yearlyPerformance(base), rollingPerformance(base), winnerContributions(base), stability, walkForward,
                base.stopOutEvents(), base.regimeExposureHistory());
    }

    private List<RiskAdjustedMomentumBacktestResult.YearlyPerformance> yearlyPerformance(RiskAdjustedMomentumBacktestResult result) {
        List<RiskAdjustedMomentumBacktestResult.YearlyPerformance> rows = new ArrayList<>();
        List<RiskAdjustedMomentumBacktestResult.EquityPoint> curve = result.equityCurve();
        for (int year : curve.stream().map(point -> point.date().getYear()).distinct().sorted().toList()) {
            List<Integer> indexes = java.util.stream.IntStream.range(0, curve.size())
                    .filter(index -> curve.get(index).date().getYear() == year).boxed().toList();
            if (indexes.isEmpty()) continue;
            int first = indexes.getFirst(), last = indexes.getLast();
            double portfolioStart = first == 0 ? result.initialCapital() : curve.get(first - 1).portfolioValue();
            double benchmarkStart = first == 0 ? result.initialCapital() : curve.get(first - 1).benchmarkValue();
            double portfolioReturn = portfolioStart == 0 ? 0 : curve.get(last).portfolioValue() / portfolioStart - 1;
            double benchmarkReturn = benchmarkStart == 0 ? 0 : curve.get(last).benchmarkValue() / benchmarkStart - 1;
            List<Double> values = new ArrayList<>(); values.add(portfolioStart);
            indexes.forEach(index -> values.add(curve.get(index).portfolioValue()));
            List<Double> monthly = new ArrayList<>(); double previous = portfolioStart;
            for (int index : indexes) {
                if (index == 0) continue;
                double current = curve.get(index).portfolioValue();
                if (previous > 0) monthly.add(current / previous - 1);
                previous = current;
            }
            double turnover = result.rebalances().stream().filter(row -> row.executionDate().getYear() == year)
                    .mapToDouble(RiskAdjustedMomentumBacktestResult.Rebalance::turnoverPercent).sum();
            double costs = result.rebalances().stream().filter(row -> row.executionDate().getYear() == year)
                    .mapToDouble(RiskAdjustedMomentumBacktestResult.Rebalance::costs).sum();
            double winRate = monthly.isEmpty() ? 0 : monthly.stream().filter(value -> value > 0).count() * 100.0 / monthly.size();
            rows.add(new RiskAdjustedMomentumBacktestResult.YearlyPerformance(year, portfolioReturn, benchmarkReturn,
                    portfolioReturn - benchmarkReturn, maximumDrawdown(values), turnover, costs, winRate));
        }
        return rows;
    }

    private List<RiskAdjustedMomentumBacktestResult.RollingPerformance> rollingPerformance(RiskAdjustedMomentumBacktestResult result) {
        int months = 12; List<RiskAdjustedMomentumBacktestResult.RollingPerformance> rows = new ArrayList<>();
        List<RiskAdjustedMomentumBacktestResult.EquityPoint> curve = result.equityCurve();
        for (int end = months; end < curve.size(); end++) {
            int start = end - months; double portfolioStart = curve.get(start).portfolioValue();
            double benchmarkStart = curve.get(start).benchmarkValue();
            double portfolioReturn = portfolioStart == 0 ? 0 : curve.get(end).portfolioValue() / portfolioStart - 1;
            double benchmarkReturn = benchmarkStart == 0 ? 0 : curve.get(end).benchmarkValue() / benchmarkStart - 1;
            List<RiskAdjustedMomentumBacktestResult.EquityPoint> window = curve.subList(start, end + 1);
            List<Double> returns = periodReturns(window, RiskAdjustedMomentumBacktestResult.EquityPoint::portfolioValue);
            rows.add(new RiskAdjustedMomentumBacktestResult.RollingPerformance(curve.get(end).date(), months,
                    portfolioReturn, benchmarkReturn, portfolioReturn - benchmarkReturn,
                    maximumDrawdown(window.stream().map(RiskAdjustedMomentumBacktestResult.EquityPoint::portfolioValue).toList()),
                    standardDeviation(returns) * Math.sqrt(12)));
        }
        return rows;
    }

    private List<RiskAdjustedMomentumBacktestResult.WinnerContribution> winnerContributions(RiskAdjustedMomentumBacktestResult result) {
        Map<String, Double> realized = new HashMap<>();
        result.rebalances().stream().flatMap(row -> row.decisions().stream())
                .filter(decision -> decision.realizedProfitLoss() != null)
                .forEach(decision -> realized.merge(decision.ticker(), decision.realizedProfitLoss(), Double::sum));
        Map<String, Double> unrealized = result.finalPositions().stream().collect(Collectors.toMap(
                RiskAdjustedMomentumBacktestResult.Position::ticker, RiskAdjustedMomentumBacktestResult.Position::profitLoss));
        Set<String> tickers = new HashSet<>(realized.keySet()); tickers.addAll(unrealized.keySet());
        double netProfit = result.finalValue() - result.initialCapital();
        return tickers.stream().map(ticker -> {
                    double closed = realized.getOrDefault(ticker, 0.0), open = unrealized.getOrDefault(ticker, 0.0);
                    double total = closed + open;
                    return new RiskAdjustedMomentumBacktestResult.WinnerContribution(ticker, closed, open, total,
                            netProfit == 0 ? 0 : total / netProfit * 100);
                }).sorted(Comparator.comparingDouble(RiskAdjustedMomentumBacktestResult.WinnerContribution::totalContribution).reversed())
                .toList();
    }

    private List<int[]> parameterPairs(int entryRank, int retentionRank) {
        return java.util.stream.Stream.of(Math.max(5, entryRank - 5), entryRank, Math.min(20, entryRank + 5)).distinct()
                .flatMap(entry -> java.util.stream.Stream.of(Math.max(entry, retentionRank - 5),
                                Math.max(entry, retentionRank), Math.max(entry, retentionRank + 5))
                        .distinct().map(retention -> new int[]{entry, retention})).toList();
    }

    private List<RiskAdjustedMomentumBacktestResult.ParameterStability> parameterStability(RiskAdjustedMomentumBacktestResult base,
            int entryRank, int retentionRank, String benchmark, double transactionCostPercent,
            double slippagePercent, double riskFreeRatePercent, String rebalanceMode,
            double bufferAmount, double maximumLeverageAmount, double borrowingInterestRatePercent,
            String stopModel, double trailingStopPercent, int atrPeriod, double atrMultiplier,
            int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent, double weakExposureCapPercent,
            BreadthExposureAdapter breadthExposureAdapter) {
        List<RiskAdjustedMomentumBacktestResult.ParameterStability> rows = new ArrayList<>();
        for (int[] pair : parameterPairs(entryRank, retentionRank)) {
            RiskAdjustedMomentumBacktestResult run = pair[0] == entryRank && pair[1] == retentionRank ? base
                    : runCore(base.startDate(), base.endDate(), base.initialCapital(), pair[0], pair[1], benchmark,
                    transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                    bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                    stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                    breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter);
            double turnover = run.rebalances().stream().mapToDouble(RiskAdjustedMomentumBacktestResult.Rebalance::turnoverPercent).sum();
            rows.add(new RiskAdjustedMomentumBacktestResult.ParameterStability(pair[0], pair[1], run.totalReturn(),
                    run.cagr(), run.maximumDrawdown(), run.sharpeRatio(), turnover, run.totalCosts()));
        }
        return rows;
    }

    private List<RiskAdjustedMomentumBacktestResult.WalkForwardWindow> walkForward(RiskAdjustedMomentumBacktestResult base,
            List<RiskAdjustedMomentumBacktestResult.ParameterStability> stability, String benchmark,
            double transactionCostPercent, double slippagePercent, double riskFreeRatePercent,
            String rebalanceMode, double bufferAmount, double maximumLeverageAmount,
            double borrowingInterestRatePercent, String stopModel, double trailingStopPercent, int atrPeriod,
            double atrMultiplier, int cooldownWeeks, int benchmarkSmaPeriod, double breadthThresholdPercent,
            double weakExposureCapPercent, BreadthExposureAdapter breadthExposureAdapter) {
        List<RiskAdjustedMomentumBacktestResult.WalkForwardWindow> rows = new ArrayList<>();
        LocalDate testStart = base.startDate().plusYears(2);
        while (testStart.isBefore(base.endDate().minusMonths(2))) {
            LocalDate testEnd = testStart.plusYears(1).minusDays(1);
            if (testEnd.isAfter(base.endDate())) testEnd = base.endDate();
            LocalDate trainingEnd = testStart.minusDays(1);
            RiskAdjustedMomentumBacktestResult best = null; int bestEntry = 0, bestRetention = 0;
            for (RiskAdjustedMomentumBacktestResult.ParameterStability candidate : stability) {
                RiskAdjustedMomentumBacktestResult training = runCore(base.startDate(), trainingEnd, base.initialCapital(),
                        candidate.entryRank(), candidate.retentionRank(), benchmark, transactionCostPercent,
                        slippagePercent, riskFreeRatePercent, rebalanceMode, bufferAmount,
                        maximumLeverageAmount, borrowingInterestRatePercent,
                        stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                        breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter);
                if (best == null || training.sharpeRatio() > best.sharpeRatio()) {
                    best = training; bestEntry = candidate.entryRank(); bestRetention = candidate.retentionRank();
                }
            }
            if (best != null) {
                RiskAdjustedMomentumBacktestResult test = runCore(testStart, testEnd, base.initialCapital(), bestEntry,
                        bestRetention, benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent,
                        rebalanceMode, bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent,
                        stopModel, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks, benchmarkSmaPeriod,
                        breadthThresholdPercent, weakExposureCapPercent, breadthExposureAdapter);
                rows.add(new RiskAdjustedMomentumBacktestResult.WalkForwardWindow(base.startDate(), trainingEnd, testStart,
                        testEnd, bestEntry, bestRetention, best.sharpeRatio(), test.totalReturn(), test.cagr(),
                        test.maximumDrawdown(), test.sharpeRatio(), test.benchmarkReturn(), test.excessReturn()));
            }
            testStart = testStart.plusYears(1);
        }
        return rows;
    }

    private List<Double> periodReturns(List<RiskAdjustedMomentumBacktestResult.EquityPoint> curve,
                                       ToDoubleFunction<RiskAdjustedMomentumBacktestResult.EquityPoint> getter) {
        List<Double> result = new ArrayList<>();
        for (int index = 1; index < curve.size(); index++) {
            double previous = getter.applyAsDouble(curve.get(index - 1));
            double current = getter.applyAsDouble(curve.get(index));
            if (previous > 0) result.add(current / previous - 1);
        }
        return result;
    }

    private double standardDeviation(List<Double> values) {
        if (values.size() < 2) return 0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    private double maximumDrawdown(List<Double> values) {
        double peak = 0, maximumDrawdown = 0;
        for (double value : values) {
            peak = Math.max(peak, value);
            if (peak > 0) maximumDrawdown = Math.min(maximumDrawdown, value / peak - 1);
        }
        return maximumDrawdown;
    }

    private double portfolioValue(Map<String, Holding> holdings,
                                  Map<String, NavigableMap<LocalDate, OHLCV>> stocks,
                                  LocalDate valuationDate, double cashBalance, double bufferAmount) {
        double marketValue = holdings.entrySet().stream().mapToDouble(entry -> {
            OHLCV bar = barAtOrBefore(stocks.get(entry.getKey()), valuationDate);
            double price = bar == null ? entry.getValue().entryPrice : bar.getClose();
            return entry.getValue().quantity * price;
        }).sum();
        return marketValue + cashBalance - bufferAmount;
    }

    /** Normalized inverse-volatility weights for the selected tickers, normalized to sum to 1 across `slots`. */
    private Map<String, Double> inverseVolWeights(Set<String> selected, Map<String, RankedStock> byTicker, int slots) {
        if (selected.isEmpty()) return Map.of();
        Map<String, Double> inverseVol = new LinkedHashMap<>();
        for (String ticker : selected) {
            RankedStock row = byTicker.get(ticker);
            double vol = row == null || row.volatility <= 0 ? 1.0 : row.volatility;
            inverseVol.put(ticker, 1.0 / vol);
        }
        double sum = inverseVol.values().stream().mapToDouble(Double::doubleValue).sum();
        double slotWeight = 1.0 / Math.max(1, slots);
        Map<String, Double> weights = new LinkedHashMap<>();
        inverseVol.forEach((ticker, value) -> weights.put(ticker, sum == 0 ? slotWeight : value / sum * (selected.size() * slotWeight)));
        return weights;
    }

    private List<RankedStock> rank(Map<String, NavigableMap<LocalDate, OHLCV>> stocks, LocalDate date) {
        List<MutableRank> rows = new ArrayList<>();
        int skip = RiskAdjustedMomentumConstants.SKIP_MONTH_BARS;
        stocks.forEach((ticker, series) -> {
            List<OHLCV> bars = new ArrayList<>(series.headMap(date, true).values());
            if (bars.size() >= RiskAdjustedMomentumConstants.MIN_DATA_POINTS) {
                int last = bars.size() - 1;
                double skippedClose = bars.get(last - skip).getClose();
                double price12 = bars.get(last - 252 - skip).getClose(), price6 = bars.get(last - 126).getClose(), price3 = bars.get(last - 63).getClose();
                double close = bars.get(last).getClose();
                if (close > 0 && skippedClose > 0 && price12 > 0 && price6 > 0 && price3 > 0) {
                    double r12 = skippedClose / price12 - 1, r6 = close / price6 - 1, r3 = close / price3 - 1;
                    if (Double.isFinite(r12) && r12 > 0 && r6 > 0 && r3 > 0) {
                        double vol = trailingVolatility(bars, last);
                        rows.add(new MutableRank(ticker, r12, r6, r3, vol));
                    }
                }
            }
        });
        assign(rows, row -> row.r12, (row, rank) -> row.rank12 = rank);
        assign(rows, row -> row.r6, (row, rank) -> row.rank6 = rank);
        assign(rows, row -> row.r3, (row, rank) -> row.rank3 = rank);
        rows.forEach(row -> row.total = row.rank12 * RiskAdjustedMomentumConstants.WEIGHT_12_MONTHS
                + row.rank6 * RiskAdjustedMomentumConstants.WEIGHT_6_MONTHS
                + row.rank3 * RiskAdjustedMomentumConstants.WEIGHT_3_MONTHS);
        List<MutableRank> ordered = rows.stream().sorted(Comparator.comparingInt((MutableRank row) -> row.total).thenComparing(row -> row.ticker)).toList();
        List<RankedStock> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            MutableRank row = ordered.get(i);
            result.add(new RankedStock(row.ticker, row.rank12, row.rank6, row.rank3, row.total, i + 1, row.volatility));
        }
        return result;
    }

    private double trailingVolatility(List<OHLCV> bars, int lastIndex) {
        int window = RiskAdjustedMomentumConstants.VOLATILITY_LOOKBACK_BARS;
        int from = Math.max(1, lastIndex - window + 1);
        List<Double> returns = new ArrayList<>();
        for (int i = from; i <= lastIndex; i++) {
            double previous = bars.get(i - 1).getClose(), current = bars.get(i).getClose();
            if (previous > 0) returns.add(current / previous - 1);
        }
        if (returns.size() < 2) return 0;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).sum() / (returns.size() - 1);
        return Math.sqrt(variance) * Math.sqrt(252);
    }

    private double stopLevel(String model, Holding holding, NavigableMap<LocalDate, OHLCV> series, LocalDate date,
                             double trailingStopPercent, int atrPeriod, double atrMultiplier) {
        return RiskAdjustedMomentumRiskOverlayUtil.stopLevel(model, holding.entryPrice, holding.peak, series, date,
                trailingStopPercent, atrPeriod, atrMultiplier);
    }

    private double breadth(Map<String, NavigableMap<LocalDate, OHLCV>> stocks, LocalDate date, int period) {
        return RiskAdjustedMomentumRiskOverlayUtil.breadth(stocks, date, period);
    }

    private boolean aboveSma(NavigableMap<LocalDate, OHLCV> series, LocalDate date, int period) {
        return RiskAdjustedMomentumRiskOverlayUtil.aboveSma(series, date, period);
    }

    private boolean tradableInPeriod(NavigableMap<LocalDate, OHLCV> series, LocalDate signalDate, LocalDate nextSignal) {
        LocalDate execution = series == null ? null : series.higherKey(signalDate);
        return execution != null && !execution.isAfter(nextSignal);
    }

    private void assign(List<MutableRank> rows, ToDoubleFunction<MutableRank> getter, RankSetter setter) {
        List<MutableRank> sorted = rows.stream().sorted(Comparator.comparingDouble(getter).reversed()).toList();
        for (int i = 0; i < sorted.size(); i++) setter.set(sorted.get(i), i + 1);
    }

    private Map<String, NavigableMap<LocalDate, OHLCV>> normalize(Map<String, List<OHLCV>> source) {
        return RiskAdjustedMomentumRiskOverlayUtil.normalize(source);
    }

    private List<LocalDate> monthlySignals(Map<String, NavigableMap<LocalDate, OHLCV>> stocks, LocalDate start, LocalDate end) {
        LocalDate firstSignalWindow = start.minusMonths(1).withDayOfMonth(1);
        return stocks.values().stream().flatMap(s -> s.keySet().stream())
                .filter(d -> !d.isBefore(firstSignalWindow) && !d.isAfter(end))
                .collect(Collectors.groupingBy(YearMonth::from, TreeMap::new, Collectors.collectingAndThen(Collectors.maxBy(LocalDate::compareTo), Optional::orElseThrow)))
                .values().stream().toList();
    }

    private NavigableMap<LocalDate, OHLCV> resolveBenchmark(Map<String, NavigableMap<LocalDate, OHLCV>> indexes, String requested) {
        return RiskAdjustedMomentumRiskOverlayUtil.resolveBenchmark(indexes, requested);
    }

    private LocalDate nextSession(NavigableMap<LocalDate, OHLCV> s, LocalDate d) { return s == null ? null : s.higherKey(d); }
    private OHLCV barAfter(NavigableMap<LocalDate, OHLCV> s, LocalDate d) { Map.Entry<LocalDate, OHLCV> e = s == null ? null : s.higherEntry(d); return e == null ? null : e.getValue(); }
    private LocalDate barDateAfter(NavigableMap<LocalDate, OHLCV> s, LocalDate d) { return s.higherKey(d); }
    private OHLCV barAtOrAfter(NavigableMap<LocalDate, OHLCV> s, LocalDate d) { Map.Entry<LocalDate, OHLCV> e = s == null ? null : s.ceilingEntry(d); return e == null ? null : e.getValue(); }
    private OHLCV barAtOrBefore(NavigableMap<LocalDate, OHLCV> s, LocalDate d) { return RiskAdjustedMomentumRiskOverlayUtil.barAtOrBefore(s, d); }
    private RiskAdjustedMomentumBacktestResult.Decision decision(String ticker, String action, Integer previous, RankedStock current, LocalDate entry, double price, long qty, Double pnl) {
        return new RiskAdjustedMomentumBacktestResult.Decision(ticker, action, previous, current == null ? 0 : current.totalRankPosition,
                current == null ? 0 : current.rank12, current == null ? 0 : current.rank6, current == null ? 0 : current.rank3,
                current == null ? 0 : current.total, entry, price, qty, pnl);
    }
    private void validate(LocalDate start, LocalDate end, double capital, int entry, int retention, double costs, double slippage) {
        if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("Start date must be before end date");
        if (capital <= 0) throw new IllegalArgumentException("Initial capital must be positive");
        if (entry < 1 || entry > 50 || retention < entry) throw new IllegalArgumentException("Retention rank must be at least the entry rank");
        if (costs < 0 || slippage < 0) throw new IllegalArgumentException("Costs cannot be negative");
    }

    private static class MutableRank {
        String ticker; double r12, r6, r3, volatility; int rank12, rank6, rank3, total;
        MutableRank(String t, double a, double b, double c, double v) { ticker = t; r12 = a; r6 = b; r3 = c; volatility = v; }
    }
    private record RankedStock(String ticker, int rank12, int rank6, int rank3, int total, int totalRankPosition, double volatility) {}
    private static class Holding {
        int rank; LocalDate entryDate; double entryPrice, peak; long quantity; double invested;
        Holding(int r, LocalDate d, double p, long q, double i) { rank = r; entryDate = d; entryPrice = p; quantity = q; invested = i; peak = p; }
    }
    @FunctionalInterface private interface RankSetter { void set(MutableRank row, int rank); }
}
