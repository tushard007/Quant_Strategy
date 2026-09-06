package org.factor_investing.quant_strategy.model.response;

import java.time.LocalDate;
import java.util.List;

public record MomentumRiskOverlayResult(
        LocalDate startDate, LocalDate endDate, String benchmark, String stopModel, String evaluationFrequency,
        double baselineFinalValue, double baselineCagr, double baselineMaximumDrawdown,
        double overlayFinalValue, double overlayTotalReturn, double overlayCagr, double overlayMaximumDrawdown,
        double overlaySharpeRatio, double overlayTotalCosts, double averageExposurePercent,
        int stopExitCount, int cooldownBlockedBuyCount,
        List<EquityPoint> equityCurve, List<StopEvent> stopEvents, List<RegimePoint> regimeHistory,
        List<OverlayRebalance> rebalances, List<StabilityRun> parameterStability
) {
    public record EquityPoint(LocalDate date, double portfolioValue, double benchmarkValue, double drawdown,
                              double exposurePercent) {}
    public record StopEvent(String ticker, String stopModel, LocalDate entryDate, double entryPrice,
                            double highestClose, double stopLevel, LocalDate breachDate, double breachClose,
                            LocalDate executionDate, double executionPrice, long quantity, double realizedProfitLoss,
                            LocalDate reentryEligibleDate) {}
    public record RegimePoint(LocalDate signalDate, double breadthPercent, boolean benchmarkAboveSma,
                              double exposureCapPercent, boolean newBuysAllowed) {}
    public record OverlayRebalance(LocalDate signalDate, LocalDate executionDate, double portfolioValue,
                                   double cash, double exposurePercent, int bought, int sold, int retained,
                                   int cooldownBlocked) {}
    public record StabilityRun(String stopModel, String frequency, double stopValue, double totalReturn,
                               double cagr, double maximumDrawdown, double sharpeRatio, int stopExits,
                               double totalCosts) {}
}
