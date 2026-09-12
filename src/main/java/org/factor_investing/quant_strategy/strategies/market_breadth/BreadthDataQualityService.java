package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * Flags data-quality problems (BRD-022) and outlier/corporate-action warnings (BRD-024) in an
 * {@link AlignedBreadthDataset}. Issues never mutate the dataset — callers (e.g.
 * {@link BreadthCoverageService}) decide how to react.
 */
@Service
public class BreadthDataQualityService {

    /** A gap between consecutive trading-calendar dates wider than this is treated as a possible missing session. */
    private static final int MAX_EXPECTED_GAP_CALENDAR_DAYS = 4;

    /** A stock with no bar within this many calendar days of the calculation date is stale. */
    private static final int STALE_INSTRUMENT_THRESHOLD_DAYS = 10;

    private static final double EXTREME_PRICE_MOVE_THRESHOLD = 0.20;
    private static final double VOLUME_DISCONTINUITY_MULTIPLE = 10.0;
    private static final int VOLUME_TRAILING_AVERAGE_SESSIONS = 20;
    private static final double CORPORATE_ACTION_RATIO_TOLERANCE = 0.02;
    private static final double[] CORPORATE_ACTION_RATIOS = {0.5, 1.0 / 3, 2.0, 3.0};

    public List<BreadthDataQualityIssue> validate(AlignedBreadthDataset dataset) {
        List<BreadthDataQualityIssue> issues = new ArrayList<>(dataset.sourceIssues());
        issues.addAll(findCalendarGaps(dataset.tradingDates()));

        for (String symbol : dataset.members()) {
            NavigableMap<LocalDate, OHLCV> series = dataset.seriesBySymbol().get(symbol);
            if (series == null || series.isEmpty()) {
                issues.add(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.MISSING_DATE,
                        dataset.calculationDate(), "No daily price history is available for this universe member"));
                continue;
            }
            issues.addAll(findInvalidBars(symbol, series, dataset.calculationDate()));
            issues.addAll(findStaleness(symbol, series, dataset.calculationDate()));
            if (!series.containsKey(dataset.calculationDate())) {
                issues.add(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.MISSING_DATE,
                        dataset.calculationDate(), "No bar is available on the calculation date"));
            }
            if (!isIndicatorEligible(series, dataset.calculationDate())) {
                issues.add(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.INSUFFICIENT_HISTORY,
                        dataset.calculationDate(),
                        "Fewer than " + BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS
                                + " sessions of history available at or before the calculation date"));
            }
        }
        return issues;
    }

    public List<BreadthOutlierWarning> detectOutliers(AlignedBreadthDataset dataset) {
        List<BreadthOutlierWarning> warnings = new ArrayList<>();
        for (String symbol : dataset.members()) {
            NavigableMap<LocalDate, OHLCV> series = dataset.seriesBySymbol().get(symbol);
            if (series == null || series.size() < 2) {
                continue;
            }
            List<Map.Entry<LocalDate, OHLCV>> bars = new ArrayList<>(
                    series.headMap(dataset.calculationDate(), true).entrySet());
            for (int i = 1; i < bars.size(); i++) {
                OHLCV previous = bars.get(i - 1).getValue();
                OHLCV current = bars.get(i).getValue();
                LocalDate date = bars.get(i).getKey();
                if (previous.getClose() <= 0 || current.getClose() <= 0) {
                    continue;
                }
                double ratio = current.getClose() / previous.getClose();
                double changePercent = Math.abs(ratio - 1);

                if (changePercent > EXTREME_PRICE_MOVE_THRESHOLD) {
                    warnings.add(new BreadthOutlierWarning(symbol, date, BreadthOutlierWarning.WarningType.EXTREME_PRICE_MOVE,
                            String.format("Close moved %.1f%% from %.2f to %.2f", changePercent * 100, previous.getClose(), current.getClose())));
                }

                if (isPossibleUnadjustedCorporateAction(ratio)) {
                    warnings.add(new BreadthOutlierWarning(symbol, date, BreadthOutlierWarning.WarningType.POSSIBLE_UNADJUSTED_CORPORATE_ACTION,
                            String.format("Overnight price ratio %.3f resembles an unadjusted split/bonus", ratio)));
                }

                double trailingAverageVolume = trailingAverageVolume(bars, i);
                boolean priceMoved = changePercent > BreadthCalculationConventions.UNCHANGED_PRICE_EPSILON_PERCENT;
                if ((priceMoved && current.getVolume() == 0)
                        || (trailingAverageVolume > 0 && current.getVolume() > trailingAverageVolume * VOLUME_DISCONTINUITY_MULTIPLE)) {
                    warnings.add(new BreadthOutlierWarning(symbol, date, BreadthOutlierWarning.WarningType.VOLUME_DISCONTINUITY,
                            "Volume " + current.getVolume() + " is inconsistent with trailing average " + trailingAverageVolume));
                }
            }
        }
        return warnings;
    }

    /** Symbols with a valid bar on the calculation date and at least the required warm-up history. */
    public Set<String> indicatorEligibleSymbols(AlignedBreadthDataset dataset) {
        Set<String> eligible = new HashSet<>();
        for (String symbol : dataset.members()) {
            NavigableMap<LocalDate, OHLCV> series = dataset.seriesBySymbol().get(symbol);
            if (series != null && isIndicatorEligible(series, dataset.calculationDate())) {
                eligible.add(symbol);
            }
        }
        return eligible;
    }

    private boolean isIndicatorEligible(NavigableMap<LocalDate, OHLCV> series, LocalDate calculationDate) {
        return series.containsKey(calculationDate)
                && series.headMap(calculationDate, true).size() >= BreadthCalculationConventions.NEW_HIGH_LOW_LOOKBACK_SESSIONS;
    }

    private List<BreadthDataQualityIssue> findCalendarGaps(List<LocalDate> tradingDates) {
        List<BreadthDataQualityIssue> issues = new ArrayList<>();
        for (int i = 1; i < tradingDates.size(); i++) {
            LocalDate previous = tradingDates.get(i - 1);
            LocalDate current = tradingDates.get(i);
            if (current.equals(previous)) {
                issues.add(new BreadthDataQualityIssue(null, BreadthDataQualityIssue.IssueType.DUPLICATE_DATE, current,
                        "Trading calendar contains a duplicate date"));
            } else if (ChronoUnit.DAYS.between(previous, current) > MAX_EXPECTED_GAP_CALENDAR_DAYS) {
                issues.add(new BreadthDataQualityIssue(null, BreadthDataQualityIssue.IssueType.MISSING_DATE, current,
                        "Gap of " + ChronoUnit.DAYS.between(previous, current) + " calendar days since previous trading date " + previous));
            }
        }
        return issues;
    }

    private List<BreadthDataQualityIssue> findInvalidBars(String symbol, NavigableMap<LocalDate, OHLCV> series,
                                                           LocalDate calculationDate) {
        List<BreadthDataQualityIssue> issues = new ArrayList<>();
        for (Map.Entry<LocalDate, OHLCV> entry : series.headMap(calculationDate, true).entrySet()) {
            OHLCV bar = entry.getValue();
            if (bar.getOpen() <= 0 || bar.getHigh() <= 0 || bar.getLow() <= 0 || bar.getClose() <= 0
                    || bar.getHigh() < bar.getLow() || bar.getHigh() < bar.getOpen() || bar.getHigh() < bar.getClose()
                    || bar.getLow() > bar.getOpen() || bar.getLow() > bar.getClose()) {
                issues.add(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.INVALID_OHLCV, entry.getKey(),
                        "OHLC values are inconsistent or non-positive"));
            }
            if (bar.getVolume() < 0) {
                issues.add(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.INVALID_VOLUME, entry.getKey(),
                        "Negative volume"));
            }
        }
        return issues;
    }

    private List<BreadthDataQualityIssue> findStaleness(String symbol, NavigableMap<LocalDate, OHLCV> series, LocalDate calculationDate) {
        LocalDate latest = series.floorKey(calculationDate);
        if (latest == null) {
            return List.of(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.STALE_INSTRUMENT,
                    calculationDate, "No bar exists on or before the calculation date"));
        }
        long daysSinceLatest = ChronoUnit.DAYS.between(latest, calculationDate);
        if (daysSinceLatest > STALE_INSTRUMENT_THRESHOLD_DAYS) {
            return List.of(new BreadthDataQualityIssue(symbol, BreadthDataQualityIssue.IssueType.STALE_INSTRUMENT, calculationDate,
                    "Latest bar is " + daysSinceLatest + " days before the calculation date (" + latest + ")"));
        }
        return List.of();
    }

    private boolean isPossibleUnadjustedCorporateAction(double ratio) {
        for (double corporateActionRatio : CORPORATE_ACTION_RATIOS) {
            if (Math.abs(ratio - corporateActionRatio) / corporateActionRatio <= CORPORATE_ACTION_RATIO_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private double trailingAverageVolume(List<Map.Entry<LocalDate, OHLCV>> bars, int currentIndex) {
        int from = Math.max(0, currentIndex - VOLUME_TRAILING_AVERAGE_SESSIONS);
        if (from >= currentIndex) {
            return 0;
        }
        long sum = 0;
        int count = 0;
        for (int i = from; i < currentIndex; i++) {
            sum += bars.get(i).getValue().getVolume();
            count++;
        }
        return count == 0 ? 0 : (double) sum / count;
    }
}
