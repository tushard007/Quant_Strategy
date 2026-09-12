package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthDataQualityServiceTest {

    private final BreadthDataQualityService qualityService = new BreadthDataQualityService();
    private final BreadthCoverageService coverageService = new BreadthCoverageService(qualityService);

    @Test
    void reportsMissingCalculationDateAndSourceDuplicates() {
        LocalDate calculationDate = LocalDate.of(2025, 1, 31);
        BreadthDataQualityIssue duplicate = new BreadthDataQualityIssue("AAA",
                BreadthDataQualityIssue.IssueType.DUPLICATE_DATE, calculationDate.minusDays(1), "duplicate");
        AlignedBreadthDataset dataset = new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, calculationDate, List.of(calculationDate),
                List.of("AAA", "BBB"), Map.of("AAA", new TreeMap<>(), "BBB", series(calculationDate.minusDays(1), 1)),
                List.of(duplicate));

        List<BreadthDataQualityIssue> issues = qualityService.validate(dataset);

        assertThat(issues).contains(duplicate);
        assertThat(issues).anyMatch(issue -> issue.symbol().equals("AAA")
                && issue.issueType() == BreadthDataQualityIssue.IssueType.MISSING_DATE);
        assertThat(issues).anyMatch(issue -> issue.symbol().equals("BBB")
                && issue.issueType() == BreadthDataQualityIssue.IssueType.MISSING_DATE);
    }

    @Test
    void coverageUsesOnlySymbolsWithACompleteWarmupAndCurrentBar() {
        LocalDate calculationDate = LocalDate.of(2025, 1, 31);
        NavigableMap<LocalDate, OHLCV> complete = series(calculationDate.minusDays(251), 252);
        AlignedBreadthDataset dataset = new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, calculationDate, List.copyOf(complete.keySet()),
                List.of("AAA"), Map.of("AAA", complete), List.of());

        BreadthCoverageResult coverage = coverageService.calculateCoverage(dataset);

        assertThat(coverage.expectedCount()).isEqualTo(1);
        assertThat(coverage.observedCount()).isEqualTo(1);
        assertThat(coverage.eligibleCount()).isEqualTo(1);
        assertThat(coverage.coveragePercent()).isEqualTo(100);
        assertThat(coverage.qualityStatus()).isEqualTo(BreadthSnapshotQuality.VALID);
    }

    @Test
    void detectsInvalidBarsAndCorporateActionLikeOutliers() {
        LocalDate first = LocalDate.of(2025, 1, 30);
        LocalDate second = first.plusDays(1);
        NavigableMap<LocalDate, OHLCV> series = new TreeMap<>();
        series.put(first, bar(first, 100, 1000));
        series.put(second, new OHLCV(date(second), 50, 40, 55, 50, -1));
        AlignedBreadthDataset dataset = new AlignedBreadthDataset(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, second, List.of(first, second), List.of("AAA"),
                Map.of("AAA", series), List.of());

        assertThat(qualityService.validate(dataset))
                .anyMatch(issue -> issue.issueType() == BreadthDataQualityIssue.IssueType.INVALID_OHLCV)
                .anyMatch(issue -> issue.issueType() == BreadthDataQualityIssue.IssueType.INVALID_VOLUME);
        assertThat(qualityService.detectOutliers(dataset))
                .anyMatch(warning -> warning.warningType() == BreadthOutlierWarning.WarningType.EXTREME_PRICE_MOVE)
                .anyMatch(warning -> warning.warningType()
                        == BreadthOutlierWarning.WarningType.POSSIBLE_UNADJUSTED_CORPORATE_ACTION);
    }

    private NavigableMap<LocalDate, OHLCV> series(LocalDate start, int count) {
        NavigableMap<LocalDate, OHLCV> result = new TreeMap<>();
        for (int index = 0; index < count; index++) {
            LocalDate date = start.plusDays(index);
            result.put(date, bar(date, 100 + index, 1000));
        }
        return result;
    }

    private OHLCV bar(LocalDate date, double close, long volume) {
        return new OHLCV(date(date), close, close, close, close, volume);
    }

    private Date date(LocalDate date) {
        return Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
