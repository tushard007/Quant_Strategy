package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.NSEIndexMasterDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
class BreadthReferenceDataValidationServiceTest {

    @Test
    void distinguishesMissingInstrumentKeyMissingHistoryAndStaleHistory() {
        LocalDate currentDate = LocalDate.of(2025, 2, 1);
        NSEIndexMasterData nifty50 = master(1L, "NIFTY50", "Nifty 50", null);
        NSEIndexMasterData nifty500 = master(2L, "NIFTY_500", "NIFTY 500", "NSE_INDEX|Nifty 500");
        NSEIndexMasterData nifty200 = master(3L, "NIFTY_200", "NIFTY 200", "NSE_INDEX|Nifty 200");
        NSEIndexMasterDataRepository masterRepository = TestRepositoryProxy.create(
                NSEIndexMasterDataRepository.class, (method, arguments) ->
                        method.getName().equals("findAll") ? List.of(nifty50, nifty500, nifty200) : null);
        IndexPriceDataRepository priceRepository = TestRepositoryProxy.create(IndexPriceDataRepository.class,
                (method, arguments) -> {
                    if (!method.getName().equals("findByNseIndexMasterData_IdAndTimeFrame")) return null;
                    return Long.valueOf(3).equals(arguments[0])
                            ? Optional.of(indexPrices(LocalDate.of(2025, 1, 1))) : Optional.empty();
                });
        BreadthReferenceDataValidationService service =
                new BreadthReferenceDataValidationService(masterRepository, priceRepository);

        BreadthReferenceDataReport report = service.checkRequiredIndices(currentDate);

        assertThat(report.issues()).anyMatch(issue -> issue.symbol().equals("NIFTY 50")
                && issue.issueType() == IndexReferenceDataIssue.IssueType.MISSING_INSTRUMENT_KEY);
        assertThat(report.issues()).anyMatch(issue -> issue.symbol().equals("NIFTY 500")
                && issue.issueType() == IndexReferenceDataIssue.IssueType.MISSING_HISTORY);
        assertThat(report.issues()).anyMatch(issue -> issue.symbol().equals("NIFTY 200")
                && issue.issueType() == IndexReferenceDataIssue.IssueType.STALE_HISTORY);
    }

    private NSEIndexMasterData master(Long id, String symbol, String indexName, String instrumentKey) {
        NSEIndexMasterData master = new NSEIndexMasterData();
        master.setId(id);
        master.setSymbol(symbol);
        master.setIndexName(indexName);
        master.setInstrumentKey(instrumentKey);
        return master;
    }

    private IndexPricesJson indexPrices(LocalDate date) {
        IndexPricesJson prices = new IndexPricesJson();
        prices.setOhlcvData(List.of(new OHLCV(Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)),
                100, 100, 100, 100, 0)));
        return prices;
    }
}
