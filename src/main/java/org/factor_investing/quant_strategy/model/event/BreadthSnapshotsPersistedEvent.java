package org.factor_investing.quant_strategy.model.event;

import org.factor_investing.quant_strategy.model.NiftyIndexName;

import java.time.LocalDate;

/** Published only after the calculation orchestrator has persisted all requested snapshots. */
public record BreadthSnapshotsPersistedEvent(NiftyIndexName universe,
                                             LocalDate fromDate,
                                             LocalDate toDate,
                                             int snapshotCount) {
}
