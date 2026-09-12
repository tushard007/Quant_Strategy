package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertEvent;
import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.repository.BreadthAlertEventRepository;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.service.BreadthAlertService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthAlertServiceTest {

    @Test
    void persistsInAppAlertOnceAcrossRepeatedRecalculations() {
        LocalDate currentDate = LocalDate.of(2026, 9, 11);
        BreadthDailySnapshot previous = snapshot(currentDate.minusDays(1), 49.0);
        BreadthDailySnapshot current = snapshot(currentDate, 51.0);
        BreadthDailySnapshotRepository snapshotRepository = TestRepositoryProxy.create(
                BreadthDailySnapshotRepository.class, (method, arguments) -> switch (method.getName()) {
                    case "findByUniverseAndTradingDateBetweenOrderByTradingDateAsc" -> List.of(current);
                    case "findFirstByUniverseAndTradingDateLessThanAndQualityStatusOrderByTradingDateDesc" ->
                            Optional.of(previous);
                    default -> null;
                });
        List<BreadthAlertEvent> stored = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();
        BreadthAlertEventRepository alertRepository = TestRepositoryProxy.create(
                BreadthAlertEventRepository.class, (method, arguments) -> switch (method.getName()) {
                    case "findByUniverseAndTriggerDateBetween" -> List.copyOf(stored);
                    case "saveAll" -> {
                        List<BreadthAlertEvent> values = new ArrayList<>();
                        ((Iterable<?>) arguments[0]).forEach(value -> values.add((BreadthAlertEvent) value));
                        values.forEach(event -> {
                            dedupKeys.add(event.getDedupKey());
                            stored.add(event);
                        });
                        yield values;
                    }
                    default -> null;
                });
        BreadthAlertService service = new BreadthAlertService(snapshotRepository, alertRepository,
                new BreadthAlertRuleService());

        var first = service.evaluatePersistedRange(NiftyIndexName.NIFTY500, currentDate, currentDate);
        var second = service.evaluatePersistedRange(NiftyIndexName.NIFTY500, currentDate, currentDate);

        assertThat(first).singleElement().satisfies(alert -> {
            assertThat(alert.alertType()).isEqualTo(BreadthAlertType.BREADTH_50D_CROSS_50_UP);
            assertThat(alert.deliveryState()).isEqualTo(AlertDeliveryState.SENT);
            assertThat(alert.triggerValues()).containsEntry(BreadthMetricKeys.BREADTH_50D_PERCENT, 51.0);
        });
        assertThat(second).isEmpty();
        assertThat(stored).hasSize(1);
    }

    private BreadthDailySnapshot snapshot(LocalDate date, double breadth50) {
        BreadthDailySnapshot snapshot = new BreadthDailySnapshot();
        snapshot.setUniverse(NiftyIndexName.NIFTY500);
        snapshot.setTradingDate(date);
        snapshot.setQualityStatus(BreadthSnapshotQuality.VALID);
        snapshot.setIndicatorValues(Map.of(BreadthMetricKeys.BREADTH_50D_PERCENT, breadth50));
        return snapshot;
    }
}
