package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BreadthScoreService {

    public BreadthScoreResult score(Map<String, Double> metrics, BreadthSnapshotQuality quality,
                                    BreadthScoreParameters parameters) {
        Map<String, Double> components = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();

        boolean adRising = flag(metrics, BreadthMetricKeys.AD_LINE_RISING);
        add(components, reasons, parameters, "AD_LINE_TREND", adRising ? 1 : 0,
                adRising ? "A/D Line and participation are rising" : "A/D Line trend is not confirmed");

        double breadth50 = value(metrics, BreadthMetricKeys.BREADTH_50D_PERCENT);
        double breadth50Factor = breadth50 >= 60 && flag(metrics, BreadthMetricKeys.BREADTH_50D_RISING)
                ? 1 : breadth50 >= 55 ? 0.75 : breadth50 >= 50 ? 0.5 : 0;
        add(components, reasons, parameters, "BREADTH_50D", breadth50Factor,
                String.format("%.1f%% of eligible stocks are above the 50-day EMA", breadth50));

        double breadth200 = value(metrics, BreadthMetricKeys.BREADTH_200D_PERCENT);
        add(components, reasons, parameters, "BREADTH_200D", breadth200 >= 50 ? 1 : breadth200 >= 40 ? 0.5 : 0,
                String.format("%.1f%% of eligible stocks are above the 200-day EMA", breadth200));

        double oscillator = value(metrics, BreadthMetricKeys.MCCLELLAN_OSCILLATOR);
        add(components, reasons, parameters, "MCCLELLAN_OSCILLATOR", oscillator > 0 ? 1 : 0,
                String.format("McClellan Oscillator is %.2f", oscillator));

        double summationTrend = value(metrics, BreadthMetricKeys.SUMMATION_TREND);
        add(components, reasons, parameters, "MCCLELLAN_SUMMATION_INDEX", summationTrend > 0 ? 1 : 0,
                summationTrend > 0 ? "McClellan Summation Index is rising" : "McClellan Summation Index is not rising");

        double netNewHighs = value(metrics, BreadthMetricKeys.NET_NEW_HIGHS);
        boolean expanding = flag(metrics, BreadthMetricKeys.NET_NEW_HIGHS_EXPANDING_5D)
                || flag(metrics, BreadthMetricKeys.NET_NEW_HIGHS_EXPANDING_10D);
        add(components, reasons, parameters, "NET_NEW_HIGHS_LOWS",
                netNewHighs > 0 && expanding ? 1 : netNewHighs > 0 ? 0.5 : 0,
                String.format("Net new highs are %.0f and %s", netNewHighs, expanding ? "expanding" : "not expanding"));

        double zweig = value(metrics, BreadthMetricKeys.ZWEIG_RATIO_10D);
        add(components, reasons, parameters, "ZWEIG_BREADTH_THRUST",
                flag(metrics, BreadthMetricKeys.ZWEIG_THRUST) ? 1 : zweig >= 0.55 ? 0.5 : 0,
                String.format("Ten-day Zweig ratio is %.3f", zweig));

        double trin = value(metrics, BreadthMetricKeys.TRIN);
        add(components, reasons, parameters, "VOLUME_BREADTH_TRIN",
                trin > 0 && trin < 1 ? 1 : trin <= 1.2 && trin > 0 ? 0.5 : 0,
                String.format("TRIN is %.3f", trin));

        double bullishPercent = value(metrics, BreadthMetricKeys.BULLISH_PERCENT_PROXY);
        add(components, reasons, parameters, "BULLISH_PERCENT_PROXY",
                bullishPercent >= 50 ? 1 : bullishPercent >= 40 ? 0.5 : 0,
                String.format("Bullish-percent proxy is %.1f%%", bullishPercent));

        double sectors = value(metrics, BreadthMetricKeys.SECTOR_PARTICIPATION);
        add(components, reasons, parameters, "SECTOR_PARTICIPATION", sectors >= 6 ? 1 : sectors >= 5 ? 0.5 : 0,
                String.format("%.0f of 11 sectors are above their 50-day EMA", sectors));

        boolean bearishDivergence = flag(metrics, BreadthMetricKeys.BEARISH_DIVERGENCE);
        boolean bullishConfirmation = flag(metrics, BreadthMetricKeys.BULLISH_CONFIRMATION);
        add(components, reasons, parameters, "PRICE_BREADTH_CONFIRMATION",
                bullishConfirmation ? 1 : bearishDivergence ? 0 : 0.5,
                bullishConfirmation ? "Price and breadth confirm each other"
                        : bearishDivergence ? "Price is not confirmed by breadth" : "No decisive confirmation or divergence");

        double score = components.values().stream().mapToDouble(Double::doubleValue).sum();
        BreadthRegime regime = score >= parameters.greenThreshold() ? BreadthRegime.GREEN
                : score >= parameters.amberThreshold() ? BreadthRegime.AMBER : BreadthRegime.RED;
        if (quality == BreadthSnapshotQuality.INVALID) {
            regime = BreadthRegime.RED;
            reasons.put("QUALITY_OVERRIDE", "Invalid universe coverage forces a Red regime");
        } else if ((quality == BreadthSnapshotQuality.WARNING
                || flag(metrics, BreadthMetricKeys.INDIA_VIX_SPIKE) || bearishDivergence)
                && regime == BreadthRegime.GREEN) {
            regime = BreadthRegime.AMBER;
            reasons.put("RISK_OVERRIDE", "Data quality, India VIX, or bearish divergence caps the regime at Amber");
        }
        return new BreadthScoreResult(score, regime, Map.copyOf(components), Map.copyOf(reasons));
    }

    private void add(Map<String, Double> components, Map<String, String> reasons,
                     BreadthScoreParameters parameters, String name, double factor, String reason) {
        int weight = parameters.weights().getOrDefault(name, 0);
        components.put(name, weight * Math.max(0, Math.min(1, factor)));
        reasons.put(name, reason);
    }

    private double value(Map<String, Double> values, String key) {
        return values.getOrDefault(key, 0.0);
    }

    private boolean flag(Map<String, Double> values, String key) {
        return value(values, key) > 0.5;
    }
}
