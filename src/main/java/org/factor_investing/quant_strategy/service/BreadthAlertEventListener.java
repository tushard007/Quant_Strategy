package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.event.BreadthSnapshotsPersistedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BreadthAlertEventListener {
    private final BreadthAlertService alertService;

    public BreadthAlertEventListener(BreadthAlertService alertService) {
        this.alertService = alertService;
    }

    @EventListener
    public void afterSnapshotsPersisted(BreadthSnapshotsPersistedEvent event) {
        try {
            int count = alertService.evaluatePersistedRange(event.universe(), event.fromDate(), event.toDate()).size();
            log.info("Created {} in-app breadth alerts for {} from {} to {}", count, event.universe(),
                    event.fromDate(), event.toDate());
        } catch (RuntimeException exception) {
            log.error("Breadth snapshots were persisted, but alert evaluation failed for {} from {} to {}",
                    event.universe(), event.fromDate(), event.toDate(), exception);
        }
    }
}
