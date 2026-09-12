package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertEvent;
import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.BreadthDailySnapshot;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.BreadthAlertResponse;
import org.factor_investing.quant_strategy.repository.BreadthAlertEventRepository;
import org.factor_investing.quant_strategy.repository.BreadthDailySnapshotRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthAlertCandidate;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthAlertRuleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BreadthAlertService {
    public static final int MAX_ALERT_LIMIT = 500;

    private final BreadthDailySnapshotRepository snapshotRepository;
    private final BreadthAlertEventRepository alertRepository;
    private final BreadthAlertRuleService ruleService;

    public BreadthAlertService(BreadthDailySnapshotRepository snapshotRepository,
                               BreadthAlertEventRepository alertRepository,
                               BreadthAlertRuleService ruleService) {
        this.snapshotRepository = snapshotRepository;
        this.alertRepository = alertRepository;
        this.ruleService = ruleService;
    }

    @Transactional
    public List<BreadthAlertResponse> evaluatePersistedRange(NiftyIndexName universe,
                                                             LocalDate fromDate, LocalDate toDate) {
        List<BreadthDailySnapshot> snapshots = snapshotRepository
                .findByUniverseAndTradingDateBetweenOrderByTradingDateAsc(universe, fromDate, toDate);
        BreadthDailySnapshot previousValid = snapshotRepository
                .findFirstByUniverseAndTradingDateLessThanAndQualityStatusOrderByTradingDateDesc(
                        universe, fromDate, BreadthSnapshotQuality.VALID).orElse(null);
        Set<String> dedupKeys = alertRepository.findByUniverseAndTriggerDateBetween(universe, fromDate, toDate)
                .stream().map(BreadthAlertEvent::getDedupKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<BreadthAlertEvent> pending = new ArrayList<>();
        for (BreadthDailySnapshot current : snapshots) {
            if (current.getQualityStatus() != BreadthSnapshotQuality.VALID) continue;
            if (previousValid != null) {
                collectCandidates(current, ruleService.evaluate(previousValid, current), dedupKeys, pending);
            }
            previousValid = current;
        }
        return alertRepository.saveAll(pending).stream().map(BreadthAlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BreadthAlertResponse> history(NiftyIndexName universe, BreadthAlertType alertType,
                                              AlertDeliveryState deliveryState, LocalDate fromDate,
                                              LocalDate toDate, int limit) {
        if (limit < 1 || limit > MAX_ALERT_LIMIT) {
            throw new IllegalArgumentException("Alert history limit must be between 1 and " + MAX_ALERT_LIMIT);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Alert history start date must not be after end date");
        }
        Specification<BreadthAlertEvent> filters = (root, query, builder) -> builder.conjunction();
        if (universe != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("universe"), universe));
        }
        if (alertType != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("alertType"), alertType.name()));
        }
        if (deliveryState != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("deliveryState"), deliveryState));
        }
        if (fromDate != null) {
            filters = filters.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("triggerDate"), fromDate));
        }
        if (toDate != null) {
            filters = filters.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("triggerDate"), toDate));
        }
        Sort sort = Sort.by(Sort.Order.desc("triggerDate"), Sort.Order.desc("createdAt"));
        return alertRepository.findAll(filters, PageRequest.of(0, limit, sort)).stream()
                .map(BreadthAlertResponse::from).toList();
    }

    private void collectCandidates(BreadthDailySnapshot current, List<BreadthAlertCandidate> candidates,
                                   Set<String> dedupKeys, List<BreadthAlertEvent> pending) {
        for (BreadthAlertCandidate candidate : candidates) {
            String dedupKey = dedupKey(current.getUniverse(), current.getTradingDate(), candidate.type());
            if (!dedupKeys.add(dedupKey)) continue;
            BreadthAlertEvent event = new BreadthAlertEvent();
            event.setAlertType(candidate.type().name());
            event.setUniverse(current.getUniverse());
            event.setTriggerDate(current.getTradingDate());
            event.setTriggerValues(candidate.triggerValues());
            event.setMessage(candidate.message());
            event.setDeliveryState(AlertDeliveryState.SENT);
            event.setAttempts(0);
            event.setDedupKey(dedupKey);
            pending.add(event);
        }
    }

    private String dedupKey(NiftyIndexName universe, LocalDate date, BreadthAlertType type) {
        return universe + ":" + date + ":" + type;
    }
}
