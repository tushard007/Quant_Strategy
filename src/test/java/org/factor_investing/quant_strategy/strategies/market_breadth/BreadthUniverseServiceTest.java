package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexConstituentHistory;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.NiftyIndexStock;
import org.factor_investing.quant_strategy.repository.IndexConstituentHistoryRepository;
import org.factor_investing.quant_strategy.repository.NiftyIndexRepository;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class BreadthUniverseServiceTest {

    @Test
    void resolvesCurrentConstituentsThroughTheExistingIndexService() {
        BreadthUniverseService service = service(List.of(indexRow("AAA"), indexRow("BBB")), List.of());

        assertThat(service.resolveMembers(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, LocalDate.of(2025, 1, 31)))
                .containsExactly("AAA", "BBB");
    }

    @Test
    void normalizesAndDeduplicatesPointInTimeConstituents() {
        LocalDate date = LocalDate.of(2020, 6, 30);
        BreadthUniverseService service = service(List.of(),
                List.of(history(" bbb "), history("AAA"), history("aaa")));

        assertThat(service.resolveMembers(NiftyIndexName.NIFTY500,
                BreadthMethodology.POINT_IN_TIME_CONSTITUENTS, date))
                .containsExactly("AAA", "BBB");
    }

    private BreadthUniverseService service(List<NiftyIndexStock> currentRows,
                                            List<IndexConstituentHistory> historicalRows) {
        NiftyIndexRepository niftyRepository = TestRepositoryProxy.create(NiftyIndexRepository.class,
                (method, arguments) -> method.getName().equals("findAll") ? currentRows : null);
        NSEStockMasterDataRepository stockRepository = TestRepositoryProxy.create(
                NSEStockMasterDataRepository.class, (method, arguments) -> null);
        IndexConstituentHistoryRepository historyRepository = TestRepositoryProxy.create(
                IndexConstituentHistoryRepository.class,
                (method, arguments) -> method.getName().equals("findMembersAsOf") ? historicalRows : null);
        return new BreadthUniverseService(new NiftyIndexStockService(niftyRepository, stockRepository), historyRepository);
    }

    private NiftyIndexStock indexRow(String symbol) {
        NiftyIndexStock row = new NiftyIndexStock();
        row.setNifty500(symbol);
        return row;
    }

    private IndexConstituentHistory history(String symbol) {
        IndexConstituentHistory history = new IndexConstituentHistory();
        history.setStockSymbol(symbol);
        return history;
    }
}
