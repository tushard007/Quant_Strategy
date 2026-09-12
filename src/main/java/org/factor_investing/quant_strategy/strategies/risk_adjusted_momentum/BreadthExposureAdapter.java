package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import java.time.LocalDate;

/**
 * Injectable hook allowing a caller to override the per-period exposure cap and new-buy gate
 * computed by {@link RiskAdjustedMomentumBacktestService}'s regime overlay. Passing {@code null}
 * to any {@code runCore}/{@code addDiagnostics} overload leaves existing behavior unchanged.
 */
@FunctionalInterface
public interface BreadthExposureAdapter {
    ExposureDecision adjust(LocalDate signalDate, double baselineExposureCapPercent, boolean baselineNewBuysAllowed);

    record ExposureDecision(double exposureCapPercent, boolean newBuysAllowed) {}
}
