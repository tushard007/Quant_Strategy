package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
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

@Service
public class MomentumBacktestService {
    private final StockPriceCacheService cacheService;

    public MomentumBacktestService(StockPriceCacheService cacheService) { this.cacheService = cacheService; }

    public MomentumBacktestResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode) {
        return run(startDate,endDate,initialCapital,entryRank,retentionRank,benchmark,transactionCostPercent,
                slippagePercent,riskFreeRatePercent,rebalanceMode,0,0,0);
    }

    public MomentumBacktestResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent) {
        return run(startDate, endDate, initialCapital, entryRank, retentionRank, benchmark,
                transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, null);
    }

    public MomentumBacktestResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent, Collection<String> stockSymbols) {
        MomentumBacktestResult base = runCore(startDate, endDate, initialCapital, entryRank, retentionRank,
                benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
        return addDiagnostics(base, entryRank, retentionRank, benchmark, transactionCostPercent,
                slippagePercent, riskFreeRatePercent, rebalanceMode, bufferAmount,
                maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
    }

    MomentumBacktestResult runCore(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent) {
        return runCore(startDate, endDate, initialCapital, entryRank, retentionRank, benchmark,
                transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, null);
    }

    MomentumBacktestResult runCore(LocalDate startDate, LocalDate endDate, double initialCapital,
                                      int entryRank, int retentionRank, String benchmark,
                                      double transactionCostPercent, double slippagePercent,
                                      double riskFreeRatePercent, String rebalanceMode,
                                      double bufferAmount, double maximumLeverageAmount,
                                      double borrowingInterestRatePercent, Collection<String> stockSymbols) {
        validate(startDate, endDate, initialCapital, entryRank, retentionRank, transactionCostPercent, slippagePercent);
        if (riskFreeRatePercent < 0 || riskFreeRatePercent > 100)
            throw new IllegalArgumentException("Risk-free rate must be between 0 and 100 percent");
        boolean equalWeightMonthly = "EQUAL_WEIGHT".equalsIgnoreCase(rebalanceMode);
        if (!equalWeightMonthly && !"REPLACEMENT_ONLY".equalsIgnoreCase(rebalanceMode))
            throw new IllegalArgumentException("Rebalance mode must be REPLACEMENT_ONLY or EQUAL_WEIGHT");
        if(bufferAmount<0||maximumLeverageAmount<0||borrowingInterestRatePercent<0)
            throw new IllegalArgumentException("Buffer, leverage and borrowing interest cannot be negative");
        Map<String, NavigableMap<LocalDate, OHLCV>> stocks = normalize(cacheService.getCachedAllStockPriceData());
        if (stockSymbols != null) {
            Set<String> universe = stockSymbols.stream().filter(Objects::nonNull)
                    .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            if (universe.isEmpty()) throw new IllegalArgumentException("The selected Nifty index does not contain any stocks");
            stocks.entrySet().removeIf(entry -> !universe.contains(entry.getKey().trim().toUpperCase(Locale.ROOT)));
        }
        Map<String, NavigableMap<LocalDate, OHLCV>> indexes = normalize(cacheService.getCachedAllIndexPriceData());
        if (stocks.isEmpty()) throw new IllegalArgumentException(stockSymbols == null
                ? "No cached stock price data is available"
                : "No cached stock price data is available for the selected Nifty index");
        NavigableMap<LocalDate, OHLCV> benchmarkPrices = resolveBenchmark(indexes, benchmark);
        List<LocalDate> signals = monthlySignals(stocks, startDate, endDate);
        if (signals.size() < 2) throw new IllegalArgumentException("The selected range needs at least two monthly signal dates");

        double value = initialCapital, peak = initialCapital, totalCosts = 0;
        double cashBalance=initialCapital+bufferAmount, maximumBufferUsed=0, maximumBorrowed=0,
                borrowingInterestPaid=0, lowestCashBalance=initialCapital;
        Map<String, Holding> holdings = new LinkedHashMap<>();
        List<MomentumBacktestResult.Rebalance> rebalances = new ArrayList<>();
        List<MomentumBacktestResult.EquityPoint> curve = new ArrayList<>();
        LocalDate firstExecution = nextSession(benchmarkPrices, signals.getFirst());
        if (firstExecution == null) throw new IllegalArgumentException("Benchmark has no execution price after the first signal date");
        if (firstExecution.isAfter(startDate.plusDays(10)))
            throw new IllegalArgumentException("Benchmark " + benchmark + " does not cover the backtest start date "
                    + startDate + "; first available execution date is " + firstExecution);
        double benchmarkEntry = benchmarkPrices.get(firstExecution).getOpen();
        curve.add(new MomentumBacktestResult.EquityPoint(firstExecution, initialCapital, initialCapital, 0));

        for (int period = 0; period < signals.size() - 1; period++) {
            LocalDate signalDate = signals.get(period), nextSignal = signals.get(period + 1);
            List<RankedStock> ranks = rank(stocks, signalDate);
            if (ranks.isEmpty()) continue;
            Map<String, RankedStock> byTicker = ranks.stream().collect(Collectors.toMap(RankedStock::ticker, value1 -> value1));
            List<String> retained = holdings.keySet().stream().filter(ticker -> {
                RankedStock row = byTicker.get(ticker); return row != null && row.totalRankPosition <= retentionRank;
            }).toList();
            LinkedHashSet<String> selected = new LinkedHashSet<>(retained);
            ranks.stream().filter(row -> row.totalRankPosition <= entryRank)
                    .map(RankedStock::ticker).filter(ticker -> tradableInPeriod(stocks.get(ticker), signalDate, nextSignal))
                    .filter(ticker -> !selected.contains(ticker)).limit(Math.max(0, entryRank - selected.size())).forEach(selected::add);

            Set<String> executionTickers = new HashSet<>(selected); executionTickers.addAll(holdings.keySet());
            LocalDate executionDate = executionTickers.stream().map(stocks::get).filter(Objects::nonNull)
                    .map(series -> series.higherKey(signalDate)).filter(Objects::nonNull)
                    .filter(date -> !date.isAfter(nextSignal)).max(LocalDate::compareTo).orElse(signalDate);
            List<MomentumBacktestResult.Decision> decisions = new ArrayList<>();
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
            int slots = Math.max(1, entryRank);
            double slotWeight = 1.0 / slots;
            for (String ticker : selected) {
                RankedStock current = byTicker.get(ticker); Holding old = holdings.get(ticker);
                if (old != null) {
                    OHLCV resizeBar = barAfter(stocks.get(ticker), signalDate);
                    double rawResizePrice = resizeBar == null ? old.entryPrice : resizeBar.getOpen();
                    double targetAllocation = equalWeightMonthly ? value * slotWeight : initialCapital * slotWeight;
                    long targetQuantity = equalWeightMonthly && rawResizePrice > 0
                            ? (long)Math.floor(targetAllocation / (rawResizePrice * (1 + slippagePercent / 100.0) * (1 + transactionCostPercent / 100.0)))
                            : old.quantity;
                    long difference = targetQuantity - old.quantity;
                    if (difference > 0) {
                        double resizePrice = rawResizePrice * (1 + slippagePercent / 100.0);
                        long affordableQuantity=(long)Math.floor(Math.max(0,cashBalance+maximumLeverageAmount)/(resizePrice*(1+transactionCostPercent/100.0)));
                        difference=Math.min(difference,affordableQuantity); targetQuantity=old.quantity+difference;
                        double addedInvestment = difference * resizePrice * (1 + transactionCostPercent / 100.0);
                        totalCosts += difference * resizePrice * transactionCostPercent / 100.0
                                + difference * rawResizePrice * slippagePercent / 100.0;
                        tradedValue += difference * resizePrice;
                        cashBalance-=addedInvestment; old.invested += addedInvestment; old.quantity = targetQuantity;
                        old.entryPrice = old.invested / old.quantity;
                        decisions.add(decision(ticker,"RESIZE_UP",old.rank,current,old.entryDate,resizePrice,difference,null));
                    } else if (difference < 0) {
                        long soldQuantity = -difference;
                        double resizePrice = rawResizePrice * (1 - slippagePercent / 100.0);
                        double costBasisSold = old.invested * soldQuantity / old.quantity;
                        double proceeds = soldQuantity * resizePrice * (1 - transactionCostPercent / 100.0);
                        double realizedPnl = proceeds - costBasisSold;
                        totalCosts += soldQuantity * resizePrice * transactionCostPercent / 100.0
                                + soldQuantity * rawResizePrice * slippagePercent / 100.0;
                        tradedValue += soldQuantity * resizePrice;
                        cashBalance+=proceeds; old.invested -= costBasisSold; old.quantity = targetQuantity;
                        decisions.add(decision(ticker,"RESIZE_DOWN",old.rank,current,old.entryDate,resizePrice,soldQuantity,realizedPnl));
                    } else decisions.add(decision(ticker, "KEEP", old.rank, current, old.entryDate, old.entryPrice, old.quantity, null));
                    old.rank = current.totalRankPosition; continue;
                }
                LocalDate entryDate = barDateAfter(stocks.get(ticker), signalDate);
                if (entryDate == null || entryDate.isAfter(nextSignal)) continue;
                OHLCV bar = stocks.get(ticker).get(entryDate); if (bar == null || bar.getOpen() <= 0) continue;
                double entryPrice = bar.getOpen() * (1 + slippagePercent / 100.0);
                double targetAllocation=equalWeightMonthly?value*slotWeight:initialCapital*slotWeight;
                double affordableAllocation=Math.max(0,cashBalance+maximumLeverageAmount);
                long quantity = (long)Math.floor(Math.min(targetAllocation,affordableAllocation) / (entryPrice * (1 + transactionCostPercent / 100.0)));
                if (quantity < 1) continue;
                double invested = quantity * entryPrice * (1 + transactionCostPercent / 100.0);
                cashBalance-=invested;
                totalCosts += quantity * entryPrice * transactionCostPercent / 100.0
                        + quantity * bar.getOpen() * slippagePercent / 100.0;
                tradedValue += quantity * entryPrice;
                holdings.put(ticker, new Holding(current.totalRankPosition, entryDate, entryPrice, quantity, invested));
                decisions.add(decision(ticker, "BUY", null, current, entryDate, entryPrice, quantity, null));
            }
            double turnover = value <= 0 ? 0 : tradedValue / value;
            double costs = totalCosts - costsBeforeRebalance;
            double periodBorrowingInterest=cashBalance<0?Math.abs(cashBalance)*borrowingInterestRatePercent/100.0/12.0:0;
            cashBalance-=periodBorrowingInterest; borrowingInterestPaid+=periodBorrowingInterest;
            maximumBorrowed=Math.max(maximumBorrowed,Math.max(0,-cashBalance));
            maximumBufferUsed=Math.max(maximumBufferUsed,Math.min(bufferAmount,Math.max(0,bufferAmount-Math.max(0,cashBalance))));
            double netCash = cashBalance - bufferAmount;
            lowestCashBalance=Math.min(lowestCashBalance,netCash);
            // Value on the next signal close. The following rebalance executes after that signal,
            // preventing any price after the requested period from leaking into this result.
            LocalDate valuationDate = nextSignal;
            value = portfolioValue(holdings, stocks, valuationDate, cashBalance, bufferAmount);
            peak = Math.max(peak, value);
            Map.Entry<LocalDate, OHLCV> benchmarkValuation = benchmarkPrices.floorEntry(valuationDate);
            if (benchmarkValuation == null)
                throw new IllegalArgumentException("Benchmark " + benchmark + " has no value on or before " + valuationDate);
            double benchmarkValue = initialCapital * benchmarkValuation.getValue().getClose() / benchmarkEntry;
            curve.add(new MomentumBacktestResult.EquityPoint(valuationDate, value, benchmarkValue, value / peak - 1));
            rebalances.add(new MomentumBacktestResult.Rebalance(signalDate, executionDate, value, benchmarkValue,
                    netCash, turnover * 100, costs, decisions));
        }
        double totalReturn = value / initialCapital - 1;
        double years = Math.max(1.0 / 365.25, ChronoUnit.DAYS.between(startDate, endDate) / 365.25);
        double benchmarkFinal = curve.getLast().benchmarkValue();
        double benchmarkReturn = benchmarkFinal / initialCapital - 1;
        double portfolioCagr = Math.pow(value / initialCapital, 1 / years) - 1;
        double benchmarkCagr = Math.pow(benchmarkFinal / initialCapital, 1 / years) - 1;
        List<Double> portfolioMonthlyReturns = periodReturns(curve, MomentumBacktestResult.EquityPoint::portfolioValue);
        List<Double> benchmarkMonthlyReturns = periodReturns(curve, MomentumBacktestResult.EquityPoint::benchmarkValue);
        double annualizedVolatility = standardDeviation(portfolioMonthlyReturns) * Math.sqrt(12);
        double riskFreeRate = riskFreeRatePercent / 100.0;
        double monthlyRiskFreeRate = Math.pow(1 + riskFreeRate, 1.0 / 12) - 1;
        double downsideDeviation = Math.sqrt(portfolioMonthlyReturns.stream()
                .mapToDouble(item -> Math.pow(Math.min(0, item - monthlyRiskFreeRate), 2)).average().orElse(0)) * Math.sqrt(12);
        double benchmarkMaximumDrawdown = maximumDrawdown(curve.stream().map(MomentumBacktestResult.EquityPoint::benchmarkValue).toList());
        double sharpeRatio = annualizedVolatility == 0 ? 0 : (portfolioCagr - riskFreeRate) / annualizedVolatility;
        double sortinoRatio = downsideDeviation == 0 ? 0 : (portfolioCagr - riskFreeRate) / downsideDeviation;
        double maxDrawdown = curve.stream().mapToDouble(MomentumBacktestResult.EquityPoint::drawdown).min().orElse(0);
        double calmarRatio = maxDrawdown == 0 ? 0 : portfolioCagr / Math.abs(maxDrawdown);
        double monthlyWinRate = portfolioMonthlyReturns.isEmpty() ? 0 : portfolioMonthlyReturns.stream().filter(item -> item > 0).count() * 100.0 / portfolioMonthlyReturns.size();
        int comparablePeriods = Math.min(portfolioMonthlyReturns.size(), benchmarkMonthlyReturns.size());
        long outperformingPeriods = java.util.stream.IntStream.range(0, comparablePeriods)
                .filter(index -> portfolioMonthlyReturns.get(index) > benchmarkMonthlyReturns.get(index)).count();
        double benchmarkOutperformanceRate = comparablePeriods == 0 ? 0 : outperformingPeriods * 100.0 / comparablePeriods;
        double finalPortfolioValue = value;
        List<MomentumBacktestResult.Position> positions = holdings.entrySet().stream().map(entry -> {
            OHLCV bar = barAtOrBefore(stocks.get(entry.getKey()), endDate); Holding holding = entry.getValue();
            double current = bar == null ? holding.entryPrice : bar.getClose(), market = current * holding.quantity;
            return new MomentumBacktestResult.Position(entry.getKey(), holding.rank, holding.quantity, holding.entryPrice,
                    current, market, market - holding.invested, finalPortfolioValue == 0 ? 0 : market / finalPortfolioValue * 100);
        }).toList();
        return new MomentumBacktestResult(startDate, endDate, initialCapital, value, totalReturn,
                portfolioCagr, maxDrawdown,
                benchmark, benchmarkFinal, benchmarkReturn, benchmarkCagr,
                benchmarkMaximumDrawdown, annualizedVolatility, sharpeRatio, sortinoRatio, calmarRatio,
                monthlyWinRate, benchmarkOutperformanceRate, riskFreeRatePercent,
                equalWeightMonthly ? "EQUAL_WEIGHT" : "REPLACEMENT_ONLY",
                bufferAmount,maximumLeverageAmount,borrowingInterestRatePercent,maximumBufferUsed,
                maximumBorrowed,borrowingInterestPaid,lowestCashBalance,
                totalReturn - benchmarkReturn, rebalances.size(),
                rebalances.stream().mapToInt(item -> (int)item.decisions().stream().filter(d -> !"KEEP".equals(d.action())).count()).sum(),
                totalCosts, curve, positions, rebalances,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private MomentumBacktestResult addDiagnostics(MomentumBacktestResult base, int entryRank, int retentionRank,
            String benchmark, double transactionCostPercent, double slippagePercent,
            double riskFreeRatePercent, String rebalanceMode, double bufferAmount,
            double maximumLeverageAmount, double borrowingInterestRatePercent,
            Collection<String> stockSymbols) {
        List<MomentumBacktestResult.ParameterStability> stability = parameterStability(base, entryRank,
                retentionRank, benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent,
                rebalanceMode, bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
        List<MomentumBacktestResult.WalkForwardWindow> walkForward = walkForward(base, stability, benchmark,
                transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
        return new MomentumBacktestResult(base.startDate(), base.endDate(), base.initialCapital(), base.finalValue(),
                base.totalReturn(), base.cagr(), base.maximumDrawdown(), base.benchmark(),
                base.benchmarkFinalValue(), base.benchmarkReturn(), base.benchmarkCagr(),
                base.benchmarkMaximumDrawdown(), base.annualizedVolatility(), base.sharpeRatio(),
                base.sortinoRatio(), base.calmarRatio(), base.monthlyWinRate(),
                base.benchmarkOutperformanceRate(), base.riskFreeRatePercent(), base.rebalanceMode(),
                base.bufferAmount(), base.maximumLeverageAmount(), base.borrowingInterestRatePercent(),
                base.maximumBufferUsed(), base.maximumBorrowed(), base.borrowingInterestPaid(),
                base.lowestCashBalance(), base.excessReturn(), base.rebalanceCount(), base.tradeCount(),
                base.totalCosts(), base.equityCurve(), base.finalPositions(), base.rebalances(),
                yearlyPerformance(base), rollingPerformance(base), winnerContributions(base), stability, walkForward);
    }

    private List<MomentumBacktestResult.YearlyPerformance> yearlyPerformance(MomentumBacktestResult result) {
        List<MomentumBacktestResult.YearlyPerformance> rows = new ArrayList<>();
        List<MomentumBacktestResult.EquityPoint> curve = result.equityCurve();
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
                    .mapToDouble(MomentumBacktestResult.Rebalance::turnoverPercent).sum();
            double costs = result.rebalances().stream().filter(row -> row.executionDate().getYear() == year)
                    .mapToDouble(MomentumBacktestResult.Rebalance::costs).sum();
            double winRate = monthly.isEmpty() ? 0 : monthly.stream().filter(value -> value > 0).count() * 100.0 / monthly.size();
            rows.add(new MomentumBacktestResult.YearlyPerformance(year, portfolioReturn, benchmarkReturn,
                    portfolioReturn - benchmarkReturn, maximumDrawdown(values), turnover, costs, winRate));
        }
        return rows;
    }

    private List<MomentumBacktestResult.RollingPerformance> rollingPerformance(MomentumBacktestResult result) {
        int months = 12; List<MomentumBacktestResult.RollingPerformance> rows = new ArrayList<>();
        List<MomentumBacktestResult.EquityPoint> curve = result.equityCurve();
        for (int end = months; end < curve.size(); end++) {
            int start = end - months; double portfolioStart = curve.get(start).portfolioValue();
            double benchmarkStart = curve.get(start).benchmarkValue();
            double portfolioReturn = portfolioStart == 0 ? 0 : curve.get(end).portfolioValue() / portfolioStart - 1;
            double benchmarkReturn = benchmarkStart == 0 ? 0 : curve.get(end).benchmarkValue() / benchmarkStart - 1;
            List<MomentumBacktestResult.EquityPoint> window = curve.subList(start, end + 1);
            List<Double> returns = periodReturns(window, MomentumBacktestResult.EquityPoint::portfolioValue);
            rows.add(new MomentumBacktestResult.RollingPerformance(curve.get(end).date(), months,
                    portfolioReturn, benchmarkReturn, portfolioReturn - benchmarkReturn,
                    maximumDrawdown(window.stream().map(MomentumBacktestResult.EquityPoint::portfolioValue).toList()),
                    standardDeviation(returns) * Math.sqrt(12)));
        }
        return rows;
    }

    private List<MomentumBacktestResult.WinnerContribution> winnerContributions(MomentumBacktestResult result) {
        Map<String, Double> realized = new HashMap<>();
        result.rebalances().stream().flatMap(row -> row.decisions().stream())
                .filter(decision -> decision.realizedProfitLoss() != null)
                .forEach(decision -> realized.merge(decision.ticker(), decision.realizedProfitLoss(), Double::sum));
        Map<String, Double> unrealized = result.finalPositions().stream().collect(Collectors.toMap(
                MomentumBacktestResult.Position::ticker, MomentumBacktestResult.Position::profitLoss));
        Set<String> tickers = new HashSet<>(realized.keySet()); tickers.addAll(unrealized.keySet());
        double netProfit = result.finalValue() - result.initialCapital();
        return tickers.stream().map(ticker -> {
                    double closed = realized.getOrDefault(ticker, 0.0), open = unrealized.getOrDefault(ticker, 0.0);
                    double total = closed + open;
                    return new MomentumBacktestResult.WinnerContribution(ticker, closed, open, total,
                            netProfit == 0 ? 0 : total / netProfit * 100);
                }).sorted(Comparator.comparingDouble(MomentumBacktestResult.WinnerContribution::totalContribution).reversed())
                .toList();
    }

    private List<int[]> parameterPairs(int entryRank, int retentionRank) {
        return java.util.stream.Stream.of(Math.max(5, entryRank - 5), entryRank, Math.min(20, entryRank + 5)).distinct()
                .flatMap(entry -> java.util.stream.Stream.of(Math.max(entry, retentionRank - 5),
                                Math.max(entry, retentionRank), Math.max(entry, retentionRank + 5))
                        .distinct().map(retention -> new int[]{entry, retention})).toList();
    }

    private List<MomentumBacktestResult.ParameterStability> parameterStability(MomentumBacktestResult base,
            int entryRank, int retentionRank, String benchmark, double transactionCostPercent,
            double slippagePercent, double riskFreeRatePercent, String rebalanceMode,
            double bufferAmount, double maximumLeverageAmount, double borrowingInterestRatePercent,
            Collection<String> stockSymbols) {
        List<MomentumBacktestResult.ParameterStability> rows = new ArrayList<>();
        for (int[] pair : parameterPairs(entryRank, retentionRank)) {
            MomentumBacktestResult run = pair[0] == entryRank && pair[1] == retentionRank ? base
                    : runCore(base.startDate(), base.endDate(), base.initialCapital(), pair[0], pair[1], benchmark,
                    transactionCostPercent, slippagePercent, riskFreeRatePercent, rebalanceMode,
                    bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
            double turnover = run.rebalances().stream().mapToDouble(MomentumBacktestResult.Rebalance::turnoverPercent).sum();
            rows.add(new MomentumBacktestResult.ParameterStability(pair[0], pair[1], run.totalReturn(),
                    run.cagr(), run.maximumDrawdown(), run.sharpeRatio(), turnover, run.totalCosts()));
        }
        return rows;
    }

    private List<MomentumBacktestResult.WalkForwardWindow> walkForward(MomentumBacktestResult base,
            List<MomentumBacktestResult.ParameterStability> stability, String benchmark,
            double transactionCostPercent, double slippagePercent, double riskFreeRatePercent,
            String rebalanceMode, double bufferAmount, double maximumLeverageAmount,
            double borrowingInterestRatePercent, Collection<String> stockSymbols) {
        List<MomentumBacktestResult.WalkForwardWindow> rows = new ArrayList<>();
        LocalDate testStart = base.startDate().plusYears(2);
        while (testStart.isBefore(base.endDate().minusMonths(2))) {
            LocalDate testEnd = testStart.plusYears(1).minusDays(1);
            if (testEnd.isAfter(base.endDate())) testEnd = base.endDate();
            LocalDate trainingEnd = testStart.minusDays(1);
            MomentumBacktestResult best = null; int bestEntry = 0, bestRetention = 0;
            for (MomentumBacktestResult.ParameterStability candidate : stability) {
                MomentumBacktestResult training = runCore(base.startDate(), trainingEnd, base.initialCapital(),
                        candidate.entryRank(), candidate.retentionRank(), benchmark, transactionCostPercent,
                        slippagePercent, riskFreeRatePercent, rebalanceMode, bufferAmount,
                        maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
                if (best == null || training.sharpeRatio() > best.sharpeRatio()) {
                    best = training; bestEntry = candidate.entryRank(); bestRetention = candidate.retentionRank();
                }
            }
            if (best != null) {
                MomentumBacktestResult test = runCore(testStart, testEnd, base.initialCapital(), bestEntry,
                        bestRetention, benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent,
                        rebalanceMode, bufferAmount, maximumLeverageAmount, borrowingInterestRatePercent, stockSymbols);
                rows.add(new MomentumBacktestResult.WalkForwardWindow(base.startDate(), trainingEnd, testStart,
                        testEnd, bestEntry, bestRetention, best.sharpeRatio(), test.totalReturn(), test.cagr(),
                        test.maximumDrawdown(), test.sharpeRatio(), test.benchmarkReturn(), test.excessReturn()));
            }
            testStart = testStart.plusYears(1);
        }
        return rows;
    }

    private List<Double> periodReturns(List<MomentumBacktestResult.EquityPoint> curve,
                                       ToDoubleFunction<MomentumBacktestResult.EquityPoint> getter) {
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

    private List<RankedStock> rank(Map<String, NavigableMap<LocalDate, OHLCV>> stocks, LocalDate date) {
        List<MutableRank> rows = new ArrayList<>();
        stocks.forEach((ticker, series) -> { List<OHLCV> bars = new ArrayList<>(series.headMap(date, true).values()); if (bars.size() >= 253) {
            int last = bars.size() - 1; double close = bars.get(last).getClose();
            double price12 = bars.get(last-252).getClose(), price6 = bars.get(last-126).getClose(), price3 = bars.get(last-63).getClose();
            if (close > 0 && price12 > 0 && price6 > 0 && price3 > 0) {
                double r12 = close / price12-1, r6 = close / price6-1, r3 = close / price3-1;
                if (Double.isFinite(r12) && r12 > 0 && r6 > 0 && r3 > 0)
                    rows.add(new MutableRank(ticker,r12,r6,r3)); }
        }});
        assign(rows, row -> row.r12, (row, rank) -> row.rank12=rank); assign(rows,row->row.r6,(row,rank)->row.rank6=rank); assign(rows,row->row.r3,(row,rank)->row.rank3=rank);
        rows.forEach(row -> row.total = row.rank12 * MomentumConstants.WEIGHT_12_MONTHS
                + row.rank6 * MomentumConstants.WEIGHT_6_MONTHS
                + row.rank3 * MomentumConstants.WEIGHT_3_MONTHS);
        List<MutableRank> ordered = rows.stream().sorted(Comparator.comparingInt((MutableRank row)->row.total).thenComparing(row->row.ticker)).toList();
        List<RankedStock> result = new ArrayList<>(); for(int i=0;i<ordered.size();i++){ MutableRank row=ordered.get(i); result.add(new RankedStock(row.ticker,row.rank12,row.rank6,row.rank3,row.total,i+1)); } return result;
    }
    private boolean tradableInPeriod(NavigableMap<LocalDate,OHLCV> series,LocalDate signalDate,LocalDate nextSignal){
        LocalDate execution=series==null?null:series.higherKey(signalDate);
        return execution!=null&&!execution.isAfter(nextSignal);
    }
    private void assign(List<MutableRank> rows, ToDoubleFunction<MutableRank> getter, RankSetter setter){ List<MutableRank> sorted=rows.stream().sorted(Comparator.comparingDouble(getter).reversed()).toList(); for(int i=0;i<sorted.size();i++)setter.set(sorted.get(i),i+1); }
    private Map<String,NavigableMap<LocalDate,OHLCV>> normalize(Map<String,List<OHLCV>> source){ if(source==null)return Map.of(); Map<String,NavigableMap<LocalDate,OHLCV>> result=new HashMap<>(); source.forEach((ticker,bars)->{TreeMap<LocalDate,OHLCV> map=new TreeMap<>();if(bars!=null)bars.stream().filter(Objects::nonNull).filter(b->b.getDate()!=null).forEach(b->map.put(DateUtil.convertDateToLocalDate(b.getDate()),b));result.put(ticker,map);});return result; }
    private List<LocalDate> monthlySignals(Map<String,NavigableMap<LocalDate,OHLCV>> stocks,LocalDate start,LocalDate end){LocalDate firstSignalWindow=start.minusMonths(1).withDayOfMonth(1);return stocks.values().stream().flatMap(s->s.keySet().stream()).filter(d->!d.isBefore(firstSignalWindow)&&!d.isAfter(end)).collect(Collectors.groupingBy(YearMonth::from,TreeMap::new,Collectors.collectingAndThen(Collectors.maxBy(LocalDate::compareTo),Optional::orElseThrow))).values().stream().toList();}
    private NavigableMap<LocalDate,OHLCV> resolveBenchmark(Map<String,NavigableMap<LocalDate,OHLCV>> indexes,String requested){
        String normalizedRequested=normalizeName(requested);
        List<Map.Entry<String,NavigableMap<LocalDate,OHLCV>>> exact=indexes.entrySet().stream()
                .filter(entry->normalizeName(entry.getKey()).equals(normalizedRequested)).toList();
        if(exact.size()==1)return exact.getFirst().getValue();
        List<Map.Entry<String,NavigableMap<LocalDate,OHLCV>>> partial=indexes.entrySet().stream()
                .filter(entry->normalizeName(entry.getKey()).contains(normalizedRequested)||normalizedRequested.contains(normalizeName(entry.getKey()))).toList();
        if(partial.size()==1)return partial.getFirst().getValue();
        if(partial.size()>1)throw new IllegalArgumentException("Benchmark name is ambiguous: "+requested+". Matching symbols: "+partial.stream().map(Map.Entry::getKey).sorted().toList());
        throw new IllegalArgumentException("Benchmark index not found: "+requested);
    }
    private String normalizeName(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9]","").toUpperCase();}
    private LocalDate nextSession(NavigableMap<LocalDate,OHLCV> s,LocalDate d){return s==null?null:s.higherKey(d);} private OHLCV barAfter(NavigableMap<LocalDate,OHLCV>s,LocalDate d){Map.Entry<LocalDate,OHLCV>e=s==null?null:s.higherEntry(d);return e==null?null:e.getValue();} private LocalDate barDateAfter(NavigableMap<LocalDate,OHLCV>s,LocalDate d){return s.higherKey(d);} private OHLCV barAtOrAfter(NavigableMap<LocalDate,OHLCV>s,LocalDate d){Map.Entry<LocalDate,OHLCV>e=s==null?null:s.ceilingEntry(d);return e==null?null:e.getValue();} private OHLCV barAtOrBefore(NavigableMap<LocalDate,OHLCV>s,LocalDate d){Map.Entry<LocalDate,OHLCV>e=s==null?null:s.floorEntry(d);return e==null?null:e.getValue();}
    private MomentumBacktestResult.Decision decision(String ticker,String action,Integer previous,RankedStock current,LocalDate entry,double price,long qty,Double pnl){return new MomentumBacktestResult.Decision(ticker,action,previous,current==null?0:current.totalRankPosition,current==null?0:current.rank12,current==null?0:current.rank6,current==null?0:current.rank3,current==null?0:current.total,entry,price,qty,pnl);}
    private void validate(LocalDate start,LocalDate end,double capital,int entry,int retention,double costs,double slippage){if(start==null||end==null||!start.isBefore(end))throw new IllegalArgumentException("Start date must be before end date");if(capital<=0)throw new IllegalArgumentException("Initial capital must be positive");if(entry<1||entry>50||retention<entry)throw new IllegalArgumentException("Retention rank must be at least the entry rank");if(costs<0||slippage<0)throw new IllegalArgumentException("Costs cannot be negative");}
    private static class MutableRank{String ticker;double r12,r6,r3;int rank12,rank6,rank3,total;MutableRank(String t,double a,double b,double c){ticker=t;r12=a;r6=b;r3=c;}}
    private record RankedStock(String ticker,int rank12,int rank6,int rank3,int total,int totalRankPosition){}
    private static class Holding{int rank;LocalDate entryDate;double entryPrice;long quantity;double invested;Holding(int r,LocalDate d,double p,long q,double i){rank=r;entryDate=d;entryPrice=p;quantity=q;invested=i;}}
    @FunctionalInterface private interface RankSetter{void set(MutableRank row,int rank);}
}
