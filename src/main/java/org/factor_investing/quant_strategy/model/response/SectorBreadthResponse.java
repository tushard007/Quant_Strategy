package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.NiftyIndexName;

import java.time.LocalDate;
import java.util.List;

public record SectorBreadthResponse(NiftyIndexName universe,
                                    LocalDate tradingDate,
                                    int participatingSectors,
                                    int availableSectors,
                                    List<SectorStatus> sectors) {
    public record SectorStatus(String symbol, boolean above50DayEma, double distancePercent) {
    }
}
