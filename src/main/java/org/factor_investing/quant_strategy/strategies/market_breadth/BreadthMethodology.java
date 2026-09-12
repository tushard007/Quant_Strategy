package org.factor_investing.quant_strategy.strategies.market_breadth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Constituent membership convention used for the calculation. "
        + "CURRENT_CONSTITUENTS is suitable for live monitoring; POINT_IN_TIME_CONSTITUENTS "
        + "requires dated membership history and is required for unbiased research.")
public enum BreadthMethodology {
    /** Uses today's index membership for every historical date. Fast and simple, but not valid for research backtests. */
    CURRENT_CONSTITUENTS,
    /**
     * Uses the index membership that was actually in effect on each historical date.
     * Required for research-grade backtests; avoids survivorship bias from stocks that were
     * later removed from the index and corporate-action distortions from stocks added/removed
     * mid-history. Requires historical constituent-change data (not yet sourced — see BRD-010).
     */
    POINT_IN_TIME_CONSTITUENTS
}
