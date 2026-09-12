package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.event.BreadthSnapshotsPersistedEvent;
import org.factor_investing.quant_strategy.model.request.BreadthRecalculationRequest;
import org.factor_investing.quant_strategy.service.MarketBreadthPipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketBreadthPipelineServiceTest {

    @Test
    void publishesCompletionEventOnlyAfterCalculationReturns() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        List<Object> events = new ArrayList<>();
        MarketBreadthCalculationOrchestrator orchestrator = orchestratorReturning(date);
        MarketBreadthPipelineService service = new MarketBreadthPipelineService(null, orchestrator, null, events::add);

        var response = service.recalculate(new BreadthRecalculationRequest(date, null,
                NiftyIndexName.NIFTY500, BreadthMethodology.CURRENT_CONSTITUENTS));

        assertThat(response.calculatedCount()).isEqualTo(1);
        assertThat(response.failureCount()).isZero();
        assertThat(events).singleElement().isInstanceOf(BreadthSnapshotsPersistedEvent.class);
        BreadthSnapshotsPersistedEvent event = (BreadthSnapshotsPersistedEvent) events.getFirst();
        assertThat(event.snapshotCount()).isEqualTo(1);
        assertThat(event.fromDate()).isEqualTo(date);
    }

    @Test
    void doesNotPublishCompletionEventWhenCalculationFails() {
        List<Object> events = new ArrayList<>();
        MarketBreadthCalculationOrchestrator orchestrator = new MarketBreadthCalculationOrchestrator(
                null, null, null, null, null) {
            @Override
            public List<BreadthCalculationResult> calculate(LocalDate from, LocalDate to,
                    NiftyIndexName universe, BreadthMethodology methodology) {
                throw new IllegalStateException("persistence failed");
            }
        };
        MarketBreadthPipelineService service = new MarketBreadthPipelineService(null, orchestrator, null, events::add);
        LocalDate date = LocalDate.of(2026, 9, 11);

        assertThatThrownBy(() -> service.recalculate(new BreadthRecalculationRequest(date, null,
                NiftyIndexName.NIFTY500, BreadthMethodology.CURRENT_CONSTITUENTS)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(events).isEmpty();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void rejectsAnOverlappingRecalculation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LocalDate date = LocalDate.of(2026, 9, 11);
        MarketBreadthCalculationOrchestrator orchestrator = new MarketBreadthCalculationOrchestrator(
                null, null, null, null, null) {
            @Override
            public List<BreadthCalculationResult> calculate(LocalDate from, LocalDate to,
                    NiftyIndexName universe, BreadthMethodology methodology) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test calculation timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return List.of(result(date));
            }
        };
        MarketBreadthPipelineService service = new MarketBreadthPipelineService(null, orchestrator, null,
                ignored -> { });
        BreadthRecalculationRequest request = new BreadthRecalculationRequest(date, null,
                NiftyIndexName.NIFTY500, BreadthMethodology.CURRENT_CONSTITUENTS);
        AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
        Thread firstRun = new Thread(() -> {
            try {
                service.recalculate(request);
            } catch (Throwable throwable) {
                backgroundFailure.set(throwable);
            }
        });

        firstRun.start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.recalculate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already running");
        release.countDown();
        firstRun.join(2_000);

        assertThat(firstRun.isAlive()).isFalse();
        assertThat(backgroundFailure.get()).isNull();
        assertThat(service.isRunning()).isFalse();
    }

    private MarketBreadthCalculationOrchestrator orchestratorReturning(LocalDate date) {
        return new MarketBreadthCalculationOrchestrator(null, null, null, null, null) {
            @Override
            public List<BreadthCalculationResult> calculate(LocalDate from, LocalDate to,
                    NiftyIndexName universe, BreadthMethodology methodology) {
                return List.of(result(date));
            }
        };
    }

    private static BreadthCalculationResult result(LocalDate date) {
        return new BreadthCalculationResult(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, date,
                new BreadthCoverageResult(500, 490, 480, 98.0, BreadthSnapshotQuality.VALID),
                Map.of(), new BreadthScoreResult(75, BreadthRegime.GREEN, Map.of(), Map.of()),
                List.of(), List.of());
    }
}
