package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.NiftyIndexStock;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.IndexConstituentHistoryRepository;
import org.factor_investing.quant_strategy.repository.NiftyIndexRepository;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class BreadthDatasetBuilderServiceTest {

    @Test
    void alignsDataWithoutForwardFillAndReportsDuplicateSourceDates() {
        LocalDate calculationDate = LocalDate.of(2025, 1, 31);
        LocalDate previousDate = calculationDate.minusDays(1);

        IndexPricesJson indexPrices = new IndexPricesJson();
        org.factor_investing.quant_strategy.model.NSEIndexMasterData indexMaster =
                new org.factor_investing.quant_strategy.model.NSEIndexMasterData();
        indexMaster.setSymbol("NIFTY_500");
        indexMaster.setIndexName("NIFTY 500");
        indexPrices.setNseIndexMasterData(indexMaster);
        indexPrices.setOhlcvData(List.of(bar(previousDate, 100), bar(calculationDate, 101), bar(calculationDate, 102)));

        NSEStockMasterData stock = new NSEStockMasterData();
        stock.setSymbol("AAA");
        StockPricesJson stockPrices = new StockPricesJson();
        stockPrices.setNseStockMasterData(stock);
        stockPrices.setOhlcvData(List.of(bar(previousDate, 10), bar(previousDate, 11)));
        NiftyIndexStock indexRow = new NiftyIndexStock();
        indexRow.setNifty500("AAA");
        NiftyIndexRepository niftyRepository = TestRepositoryProxy.create(NiftyIndexRepository.class,
                (method, arguments) -> method.getName().equals("findAll") ? List.of(indexRow) : null);
        NSEStockMasterDataRepository stockMasterRepository = TestRepositoryProxy.create(
                NSEStockMasterDataRepository.class, (method, arguments) -> null);
        IndexConstituentHistoryRepository historyRepository = TestRepositoryProxy.create(
                IndexConstituentHistoryRepository.class, (method, arguments) -> null);
        BreadthUniverseService universeService = new BreadthUniverseService(
                new NiftyIndexStockService(niftyRepository, stockMasterRepository), historyRepository);
        IndexPriceDataRepository indexRepository = TestRepositoryProxy.create(IndexPriceDataRepository.class,
                (method, arguments) -> method.getName().equals("findAllByTimeFrame")
                        ? List.of(indexPrices) : null);
        StockDataRepository stockRepository = TestRepositoryProxy.create(StockDataRepository.class,
                (method, arguments) -> method.getName().equals("findAllByTimeFrame") ? List.of(stockPrices) : null);
        BreadthDatasetBuilderService service =
                new BreadthDatasetBuilderService(universeService, indexRepository, stockRepository);

        AlignedBreadthDataset dataset = service.build(NiftyIndexName.NIFTY500,
                BreadthMethodology.CURRENT_CONSTITUENTS, calculationDate);

        assertThat(dataset.tradingDates()).containsExactly(previousDate, calculationDate);
        assertThat(dataset.seriesBySymbol().get("AAA")).containsOnlyKeys(previousDate);
        assertThat(dataset.seriesBySymbol().get("AAA").get(previousDate).getClose()).isEqualTo(10);
        assertThat(dataset.sourceIssues()).filteredOn(issue ->
                issue.issueType() == BreadthDataQualityIssue.IssueType.DUPLICATE_DATE).hasSize(2);
    }

    private OHLCV bar(LocalDate date, double close) {
        return new OHLCV(Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)), close, close, close, close, 1000);
    }
}
