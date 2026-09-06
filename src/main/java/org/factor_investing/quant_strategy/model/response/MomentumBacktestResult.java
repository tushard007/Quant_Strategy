package org.factor_investing.quant_strategy.model.response;

import java.time.LocalDate;
import java.util.List;

public record MomentumBacktestResult(
        LocalDate startDate, LocalDate endDate, double initialCapital, double finalValue,
        double totalReturn, double cagr, double maximumDrawdown,
        String benchmark, double benchmarkFinalValue, double benchmarkReturn, double benchmarkCagr,
        double benchmarkMaximumDrawdown, double annualizedVolatility, double sharpeRatio,
        double sortinoRatio, double calmarRatio, double monthlyWinRate,
        double benchmarkOutperformanceRate, double riskFreeRatePercent, String rebalanceMode,
        double bufferAmount, double maximumLeverageAmount, double borrowingInterestRatePercent,
        double maximumBufferUsed, double maximumBorrowed, double borrowingInterestPaid, double lowestCashBalance,
        double excessReturn, int rebalanceCount, int tradeCount, double totalCosts,
        List<EquityPoint> equityCurve, List<Position> finalPositions, List<Rebalance> rebalances,
        List<YearlyPerformance> yearlyPerformance, List<RollingPerformance> rollingPerformance,
        List<WinnerContribution> winnerContributions, List<ParameterStability> parameterStability,
        List<WalkForwardWindow> walkForwardWindows
) {
    public record EquityPoint(LocalDate date, double portfolioValue, double benchmarkValue, double drawdown) {}
    public record Position(String ticker, int totalRank, long quantity, double entryPrice,
                           double currentPrice, double marketValue, double profitLoss, double weightPercent) {}
    public record Rebalance(LocalDate signalDate, LocalDate executionDate, double portfolioValue,
                            double benchmarkValue, double cash, double turnoverPercent, double costs,
                            List<Decision> decisions) {}
    public record Decision(String ticker, String action, Integer previousRank, int currentRank,
                           int rank12, int rank6, int rank3, int totalRank,
                           LocalDate originalEntryDate, double executionPrice, long quantity,
                           Double realizedProfitLoss) {}
    public record YearlyPerformance(int year, double portfolioReturn, double benchmarkReturn,
                                    double excessReturn, double maximumDrawdown, double turnoverPercent,
                                    double costs, double monthlyWinRate) {}
    public record RollingPerformance(LocalDate endDate, int months, double portfolioReturn,
                                     double benchmarkReturn, double excessReturn,
                                     double maximumDrawdown, double annualizedVolatility) {}
    public record WinnerContribution(String ticker, double realizedProfitLoss,
                                     double unrealizedProfitLoss, double totalContribution,
                                     double contributionPercentOfNetProfit) {}
    public record ParameterStability(int entryRank, int retentionRank, double totalReturn,
                                     double cagr, double maximumDrawdown, double sharpeRatio,
                                     double turnoverPercent, double totalCosts) {}
    public record WalkForwardWindow(LocalDate trainingStart, LocalDate trainingEnd,
                                    LocalDate testStart, LocalDate testEnd,
                                    int selectedEntryRank, int selectedRetentionRank,
                                    double trainingSharpeRatio, double testReturn,
                                    double testCagr, double testMaximumDrawdown,
                                    double testSharpeRatio, double benchmarkTestReturn,
                                    double testExcessReturn) {}
}
