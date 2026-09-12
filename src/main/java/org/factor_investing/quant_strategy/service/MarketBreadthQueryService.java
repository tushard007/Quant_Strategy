package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.BreadthSnapshotResponse;
import org.factor_investing.quant_strategy.model.response.SectorBreadthResponse;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMetricKeys;
import org.factor_investing.quant_strategy.strategies.market_breadth.RequiredMarketBreadthIndex;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketBreadthQueryService {
    public static final int MAX_HISTORY_LIMIT = 2000;

    private final BreadthDailySnapshotRepository snapshotRepository;

    public MarketBreadthQueryService(BreadthDailySnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    public BreadthSnapshotResponse latest(NiftyIndexName universe) {
        return BreadthSnapshotResponse.from(latestEntity(universe));
    }

    public BreadthSnapshotResponse latest(NiftyIndexName universe, LocalDate asOf) {
        return BreadthSnapshotResponse.from(latestEntity(universe, asOf));
    }

    public List<BreadthSnapshotResponse> history(NiftyIndexName universe, LocalDate from, LocalDate to, int limit) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("A valid breadth history date range is required");
        }
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new IllegalArgumentException("History limit must be between 1 and " + MAX_HISTORY_LIMIT);
        }
        if (to.isAfter(from.plusYears(10))) {
            throw new IllegalArgumentException("Breadth history date range cannot exceed 10 years");
        }
        return snapshotRepository.findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(
                        universe, from, to, PageRequest.of(0, limit)).stream()
                .map(BreadthSnapshotResponse::from).toList();
    }

    public SectorBreadthResponse sectors(NiftyIndexName universe) {
        return sectors(universe, null);
    }

    public SectorBreadthResponse sectors(NiftyIndexName universe, LocalDate asOf) {
        BreadthDailySnapshot snapshot = latestEntity(universe, asOf);
        Map<String, Double> indicators = snapshot.getIndicatorValues() == null ? Map.of() : snapshot.getIndicatorValues();
        List<SectorBreadthResponse.SectorStatus> sectors = RequiredMarketBreadthIndex.sectorIndices().stream()
                .map(sector -> new SectorBreadthResponse.SectorStatus(sector.getSymbol(),
                        indicators.getOrDefault("SECTOR_" + sector.name() + "_ABOVE_50D", 0.0) > 0.5,
                        indicators.getOrDefault("SECTOR_" + sector.name() + "_DISTANCE_PERCENT", 0.0)))
                .toList();
        return new SectorBreadthResponse(universe, snapshot.getTradingDate(),
                indicators.getOrDefault(BreadthMetricKeys.SECTOR_PARTICIPATION, 0.0).intValue(),
                indicators.getOrDefault(BreadthMetricKeys.SECTOR_COUNT, 0.0).intValue(), sectors);
    }

    private BreadthDailySnapshot latestEntity(NiftyIndexName universe) {
        return snapshotRepository.findFirstByUniverseOrderByTradingDateDesc(universe)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "No breadth snapshot is available for " + universe));
    }

    private BreadthDailySnapshot latestEntity(NiftyIndexName universe, LocalDate asOf) {
        if (asOf == null) {
            return latestEntity(universe);
        }
        return snapshotRepository.findFirstByUniverseAndTradingDateLessThanEqualOrderByTradingDateDesc(universe, asOf)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "No breadth snapshot is available for " + universe + " on or before " + asOf));
    }
}
