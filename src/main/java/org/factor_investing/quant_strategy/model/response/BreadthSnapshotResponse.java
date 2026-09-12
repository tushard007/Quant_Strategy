package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record BreadthSnapshotResponse(UUID id,
                                      NiftyIndexName universe,
                                      LocalDate tradingDate,
                                      BreadthMethodology methodology,
                                      int scoreConfigurationVersion,
                                      double coveragePercent,
                                      BreadthSnapshotQuality qualityStatus,
                                      double score,
                                      BreadthRegime regime,
                                      Map<String, Double> indicators,
                                      Map<String, Double> componentScores,
                                      Map<String, String> componentReasons) {
    public static BreadthSnapshotResponse from(BreadthDailySnapshot snapshot) {
        return new BreadthSnapshotResponse(snapshot.getId(), snapshot.getUniverse(), snapshot.getTradingDate(),
                snapshot.getMethodology(), snapshot.getScoreConfigurationVersion(), snapshot.getCoveragePercent(),
                snapshot.getQualityStatus(), snapshot.getFinalScore(), snapshot.getRegime(),
                snapshot.getIndicatorValues(), snapshot.getComponentScores(), snapshot.getComponentReasons());
    }
}
