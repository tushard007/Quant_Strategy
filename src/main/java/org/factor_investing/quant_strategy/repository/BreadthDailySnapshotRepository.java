package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BreadthDailySnapshotRepository extends JpaRepository<BreadthDailySnapshot, UUID> {
    Optional<BreadthDailySnapshot> findByUniverseAndTradingDate(NiftyIndexName universe, LocalDate tradingDate);

    List<BreadthDailySnapshot> findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(
            NiftyIndexName universe, LocalDate startDate, LocalDate endDate);

    Optional<BreadthDailySnapshot> findFirstByUniverseAndTradingDateLessThanOrderByTradingDateDesc(
            NiftyIndexName universe, LocalDate tradingDate);

    Optional<BreadthDailySnapshot> findFirstByUniverseOrderByTradingDateDesc(NiftyIndexName universe);

    Optional<BreadthDailySnapshot> findFirstByUniverseAndTradingDateLessThanEqualOrderByTradingDateDesc(
            NiftyIndexName universe, LocalDate tradingDate);

    Optional<BreadthDailySnapshot> findFirstByUniverseAndTradingDateLessThanAndQualityStatusOrderByTradingDateDesc(
            NiftyIndexName universe, LocalDate tradingDate, BreadthSnapshotQuality qualityStatus);

    List<BreadthDailySnapshot> findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(
            NiftyIndexName universe, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
