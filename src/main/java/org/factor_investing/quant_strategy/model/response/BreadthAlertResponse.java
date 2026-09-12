package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertEvent;
import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.NiftyIndexName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record BreadthAlertResponse(UUID id,
                                   BreadthAlertType alertType,
                                   NiftyIndexName universe,
                                   LocalDate triggerDate,
                                   Map<String, Double> triggerValues,
                                   String message,
                                   AlertDeliveryState deliveryState,
                                   Instant createdAt) {
    public static BreadthAlertResponse from(BreadthAlertEvent event) {
        return new BreadthAlertResponse(event.getId(), BreadthAlertType.valueOf(event.getAlertType()),
                event.getUniverse(), event.getTriggerDate(), event.getTriggerValues(), event.getMessage(),
                event.getDeliveryState(), event.getCreatedAt());
    }
}
