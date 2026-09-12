package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.model.event.BreadthSnapshotsPersistedEvent;
import org.factor_investing.quant_strategy.model.request.BreadthRecalculationRequest;
import org.factor_investing.quant_strategy.model.response.BreadthRecalculationResponse;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.factor_investing.quant_strategy.strategies.market_breadth.MarketBreadthCalculationOrchestrator;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class MarketBreadthPipelineService {
    private static final int INITIAL_HISTORY_CALENDAR_DAYS = 90;
    private static final List<NiftyIndexName> SCHEDULED_UNIVERSES =
            List.of(NiftyIndexName.NIFTY500, NiftyIndexName.NIFTY200, NiftyIndexName.NIFTY50);

    private final PriceDataService priceDataService;
    private final MarketBreadthCalculationOrchestrator calculationOrchestrator;
    private final BreadthDailySnapshotRepository snapshotRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MarketBreadthPipelineService(PriceDataService priceDataService,
                                         MarketBreadthCalculationOrchestrator calculationOrchestrator,
                                         BreadthDailySnapshotRepository snapshotRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.priceDataService = priceDataService;
        this.calculationOrchestrator = calculationOrchestrator;
        this.snapshotRepository = snapshotRepository;
        this.eventPublisher = eventPublisher;
    }

    public BreadthRecalculationResponse recalculate(BreadthRecalculationRequest request) {
        return withLock(() -> calculate(request.fromDate(), request.effectiveToDate(), request.universe(),
                request.methodology()));
    }

    public List<BreadthRecalculationResponse> runEndOfDay(LocalDate requestedDate) throws ParseException {
        return withLockChecked(() -> {
            LocalDate tradingDate = DateUtil.getFridayDateIfWeekend(requestedDate);
            updateRequiredPrices();
            verifyRequiredPrices();
            return SCHEDULED_UNIVERSES.stream()
                    .map(universe -> calculate(initialStartDate(universe, tradingDate), tradingDate, universe,
                            BreadthMethodology.CURRENT_CONSTITUENTS))
                    .toList();
        });
    }

    private LocalDate initialStartDate(NiftyIndexName universe, LocalDate tradingDate) {
        if (snapshotRepository.findFirstByUniverseOrderByTradingDateDesc(universe).isEmpty()) {
            LocalDate start = tradingDate.minusDays(INITIAL_HISTORY_CALENDAR_DAYS);
            log.info("No {} breadth snapshots exist; initializing history from {}", universe, start);
            return start;
        }
        return tradingDate;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void updateRequiredPrices() throws ParseException {
        if (!priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.STOCK)) {
            priceDataService.updateStockPriceDataFromLastDate();
        }
        if (!priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.INDEX)) {
            priceDataService.saveOrUpdateIndexPriceData(PriceFrequencey.DAILY);
        }
    }

    private void verifyRequiredPrices() {
        if (!priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.STOCK)) {
            throw new IllegalStateException("Stock price data is not current; breadth calculation was not started");
        }
        if (!priceDataService.isPriceDataUpdatedTillCurrentTradingDate(AssetDataType.INDEX)) {
            throw new IllegalStateException("Index price data is not current; breadth calculation was not started");
        }
    }

    private BreadthRecalculationResponse calculate(LocalDate from, LocalDate to, NiftyIndexName universe,
                                                    BreadthMethodology methodology) {
        int count = calculationOrchestrator.calculate(from, to, universe, methodology).size();
        eventPublisher.publishEvent(new BreadthSnapshotsPersistedEvent(universe, from, to, count));
        return new BreadthRecalculationResponse(universe, methodology, from, to, count, 0);
    }

    private <T> T withLock(java.util.function.Supplier<T> action) {
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A market-breadth calculation is already running");
        }
        try {
            return action.get();
        } finally {
            running.set(false);
        }
    }

    private <T> T withLockChecked(CheckedSupplier<T> action) throws ParseException {
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A market-breadth calculation is already running");
        }
        try {
            return action.get();
        } finally {
            running.set(false);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws ParseException;
    }
}
