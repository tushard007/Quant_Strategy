package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BreadthAlertRuleService {
    private static final double PARTICIPATION_THRESHOLD = 50.0;

    public List<BreadthAlertCandidate> evaluate(BreadthDailySnapshot previous, BreadthDailySnapshot current) {
        if (previous == null || current == null) {
            return List.of();
        }
        Map<String, Double> before = values(previous);
        Map<String, Double> now = values(current);
        List<BreadthAlertCandidate> alerts = new ArrayList<>();

        addThresholdCross(alerts, before, now, BreadthMetricKeys.BREADTH_50D_PERCENT,
                50.0, BreadthAlertType.BREADTH_50D_CROSS_50_UP,
                BreadthAlertType.BREADTH_50D_CROSS_50_DOWN, "50-day breadth");
        addThresholdCross(alerts, before, now, BreadthMetricKeys.BREADTH_50D_PERCENT,
                60.0, BreadthAlertType.BREADTH_50D_CROSS_60_UP,
                BreadthAlertType.BREADTH_50D_CROSS_60_DOWN, "50-day breadth");
        addThresholdCross(alerts, before, now, BreadthMetricKeys.BREADTH_50D_PERCENT,
                70.0, BreadthAlertType.BREADTH_50D_CROSS_70_UP,
                BreadthAlertType.BREADTH_50D_CROSS_70_DOWN, "50-day breadth");
        addThresholdCross(alerts, before, now, BreadthMetricKeys.BREADTH_200D_PERCENT,
                PARTICIPATION_THRESHOLD, BreadthAlertType.BREADTH_200D_CROSS_UP,
                BreadthAlertType.BREADTH_200D_CROSS_DOWN, "200-day breadth");
        addRisingEdge(alerts, before, now, BreadthMetricKeys.BEARISH_DIVERGENCE,
                BreadthAlertType.BEARISH_AD_DIVERGENCE,
                "NIFTY price strength is no longer confirmed by market breadth");
        addRisingEdge(alerts, before, now, BreadthMetricKeys.ZWEIG_THRUST,
                BreadthAlertType.ZWEIG_BREADTH_THRUST, "A Zweig Breadth Thrust was detected");
        addSignCross(alerts, before, now, BreadthMetricKeys.MCCLELLAN_OSCILLATOR,
                BreadthAlertType.MCCLELLAN_ZERO_CROSS_UP, BreadthAlertType.MCCLELLAN_ZERO_CROSS_DOWN,
                "McClellan Oscillator");
        addSignCross(alerts, before, now, BreadthMetricKeys.SUMMATION_TREND,
                BreadthAlertType.SUMMATION_TREND_FLIP_UP, BreadthAlertType.SUMMATION_TREND_FLIP_DOWN,
                "McClellan Summation Index trend");
        addNegativeFlip(alerts, before, now, BreadthMetricKeys.NET_NEW_HIGHS,
                BreadthAlertType.NET_NEW_HIGHS_NEGATIVE_FLIP, "Net new highs turned negative");
        addRisingEdge(alerts, before, now, BreadthMetricKeys.NARROW_LEADERSHIP,
                BreadthAlertType.SECTOR_BREADTH_NARROWING, "Sector participation narrowed materially");
        addRisingEdge(alerts, before, now, BreadthMetricKeys.INDIA_VIX_SPIKE,
                BreadthAlertType.INDIA_VIX_SPIKE, "India VIX registered a configured single-session spike");
        addBooleanCross(alerts, before, now, BreadthMetricKeys.INDIA_VIX_ABOVE_EMA,
                BreadthAlertType.INDIA_VIX_CROSS_ABOVE_EMA, BreadthAlertType.INDIA_VIX_CROSS_BELOW_EMA,
                "India VIX", "its 20-day EMA");
        return List.copyOf(alerts);
    }

    private void addThresholdCross(List<BreadthAlertCandidate> alerts, Map<String, Double> before,
                                   Map<String, Double> now, String key, double threshold,
                                   BreadthAlertType upType, BreadthAlertType downType, String label) {
        Double previous = finite(before.get(key));
        Double current = finite(now.get(key));
        if (previous == null || current == null) return;
        if (previous < threshold && current >= threshold) {
            alerts.add(candidate(upType, key, previous, current,
                    label + " crossed above " + threshold + "%"));
        } else if (previous >= threshold && current < threshold) {
            alerts.add(candidate(downType, key, previous, current,
                    label + " crossed below " + threshold + "%"));
        }
    }

    private void addSignCross(List<BreadthAlertCandidate> alerts, Map<String, Double> before,
                              Map<String, Double> now, String key, BreadthAlertType upType,
                              BreadthAlertType downType, String label) {
        Double previous = finite(before.get(key));
        Double current = finite(now.get(key));
        if (previous == null || current == null) return;
        if (previous <= 0 && current > 0) {
            alerts.add(candidate(upType, key, previous, current, label + " crossed above zero"));
        } else if (previous >= 0 && current < 0) {
            alerts.add(candidate(downType, key, previous, current, label + " crossed below zero"));
        }
    }

    private void addNegativeFlip(List<BreadthAlertCandidate> alerts, Map<String, Double> before,
                                 Map<String, Double> now, String key, BreadthAlertType type, String message) {
        Double previous = finite(before.get(key));
        Double current = finite(now.get(key));
        if (previous != null && current != null && previous >= 0 && current < 0) {
            alerts.add(candidate(type, key, previous, current, message));
        }
    }

    private void addRisingEdge(List<BreadthAlertCandidate> alerts, Map<String, Double> before,
                               Map<String, Double> now, String key, BreadthAlertType type, String message) {
        Double previous = finite(before.get(key));
        Double current = finite(now.get(key));
        if (previous != null && current != null && previous <= 0.5 && current > 0.5) {
            alerts.add(candidate(type, key, previous, current, message));
        }
    }

    private void addBooleanCross(List<BreadthAlertCandidate> alerts, Map<String, Double> before,
                                 Map<String, Double> now, String key, BreadthAlertType upType,
                                 BreadthAlertType downType, String subject, String reference) {
        Double previous = finite(before.get(key));
        Double current = finite(now.get(key));
        if (previous == null || current == null) return;
        if (previous <= 0.5 && current > 0.5) {
            alerts.add(candidate(upType, key, previous, current, subject + " crossed above " + reference));
        } else if (previous > 0.5 && current <= 0.5) {
            alerts.add(candidate(downType, key, previous, current, subject + " crossed below " + reference));
        }
    }

    private BreadthAlertCandidate candidate(BreadthAlertType type, String key, double previous,
                                             double current, String message) {
        return new BreadthAlertCandidate(type, Map.of("previous_" + key, previous, key, current), message);
    }

    private Map<String, Double> values(BreadthDailySnapshot snapshot) {
        return snapshot.getIndicatorValues() == null ? Map.of() : snapshot.getIndicatorValues();
    }

    private Double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }
}
