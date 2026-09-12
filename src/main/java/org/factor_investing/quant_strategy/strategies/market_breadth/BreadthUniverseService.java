package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.repository.IndexConstituentHistoryRepository;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Resolves which stock symbols belong to a universe. {@code CURRENT_CONSTITUENTS} always
 * reflects today's membership regardless of {@code asOfDate}; {@code POINT_IN_TIME_CONSTITUENTS}
 * queries {@link IndexConstituentHistoryRepository}, which has no imported history yet (see
 * BRD-010) and will therefore return an empty list rather than silently falling back to current
 * membership.
 */
@Service
public class BreadthUniverseService {

    private final NiftyIndexStockService niftyIndexStockService;
    private final IndexConstituentHistoryRepository indexConstituentHistoryRepository;

    public BreadthUniverseService(NiftyIndexStockService niftyIndexStockService,
                                   IndexConstituentHistoryRepository indexConstituentHistoryRepository) {
        this.niftyIndexStockService = niftyIndexStockService;
        this.indexConstituentHistoryRepository = indexConstituentHistoryRepository;
    }

    public List<String> resolveMembers(NiftyIndexName universe, BreadthMethodology methodology, LocalDate asOfDate) {
        if (methodology == BreadthMethodology.POINT_IN_TIME_CONSTITUENTS) {
            return indexConstituentHistoryRepository.findMembersAsOf(universe, asOfDate).stream()
                    .map(history -> history.getStockSymbol().trim().toUpperCase())
                    .distinct()
                    .sorted(String::compareToIgnoreCase)
                    .toList();
        }
        return niftyIndexStockService.symbolsForIndex(universe);
    }
}
