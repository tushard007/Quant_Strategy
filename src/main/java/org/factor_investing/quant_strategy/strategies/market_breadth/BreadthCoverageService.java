package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.springframework.stereotype.Service;

/** Quantifies how much of a universe has usable data on the calculation date (BRD-023). */
@Service
public class BreadthCoverageService {

    private final BreadthDataQualityService breadthDataQualityService;

    public BreadthCoverageService(BreadthDataQualityService breadthDataQualityService) {
        this.breadthDataQualityService = breadthDataQualityService;
    }

    public BreadthCoverageResult calculateCoverage(AlignedBreadthDataset dataset) {
        int expectedCount = dataset.members().size();
        long observedCount = dataset.members().stream()
                .map(dataset.seriesBySymbol()::get)
                .filter(series -> series != null && series.containsKey(dataset.calculationDate()))
                .count();
        int eligibleCount = breadthDataQualityService.indicatorEligibleSymbols(dataset).size();

        double coveragePercent = expectedCount == 0 ? 0 : eligibleCount * 100.0 / expectedCount;
        BreadthSnapshotQuality qualityStatus;
        if (coveragePercent >= BreadthCalculationConventions.MIN_COVERAGE_VALID_PERCENT) {
            qualityStatus = BreadthSnapshotQuality.VALID;
        } else if (coveragePercent >= BreadthCalculationConventions.MIN_COVERAGE_WARNING_PERCENT) {
            qualityStatus = BreadthSnapshotQuality.WARNING;
        } else {
            qualityStatus = BreadthSnapshotQuality.INVALID;
        }

        return new BreadthCoverageResult(expectedCount, (int) observedCount, eligibleCount, coveragePercent, qualityStatus);
    }
}
