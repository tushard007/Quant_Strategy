package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertEvent;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BreadthAlertEventRepository extends JpaRepository<BreadthAlertEvent, UUID>,
        JpaSpecificationExecutor<BreadthAlertEvent> {
    boolean existsByDedupKey(String dedupKey);

    Optional<BreadthAlertEvent> findByDedupKey(String dedupKey);

    List<BreadthAlertEvent> findByDeliveryStateOrderByCreatedAtAsc(AlertDeliveryState deliveryState);

    List<BreadthAlertEvent> findByUniverseAndTriggerDateBetween(NiftyIndexName universe,
                                                                LocalDate fromDate, LocalDate toDate);

}
