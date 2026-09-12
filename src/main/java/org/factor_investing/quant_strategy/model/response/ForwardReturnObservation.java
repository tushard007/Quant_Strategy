package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.BreadthRegime;

import java.time.LocalDate;

public record ForwardReturnObservation(LocalDate tradingDate, BreadthRegime regime, double finalScore, int scoreBucket,
                                        Double forwardReturn20Session, Double forwardReturn60Session) {
}
