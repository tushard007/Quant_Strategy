package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthIndexDataServiceTest {

    @Test
    void mapsStoredUnderscoreSymbolsAndFinancialServicesAliasToBreadthIndices() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        List<IndexPricesJson> rows = List.of(
                prices("NIFTY_500", "NIFTY 500", "NSE_INDEX|Nifty 500", date, 25_000),
                prices("NIFTY_FINANCIAL_SERVICES", "NIFTY FINANCIAL SERVICES",
                        "NSE_INDEX|Nifty Fin Service", date, 24_000),
                prices("INDIA_VIX", "INDIA VIX", "NSE_INDEX|India VIX", date, 12.5));
        IndexPriceDataRepository repository = TestRepositoryProxy.create(IndexPriceDataRepository.class,
                (method, arguments) -> method.getName().equals("findAllByTimeFrame") ? rows : null);

        BreadthIndexSeries result = new BreadthIndexDataService(repository).load(NiftyIndexName.NIFTY500);

        assertThat(result.benchmark()).containsKey(date);
        assertThat(result.sectors().get(RequiredMarketBreadthIndex.NIFTY_FIN_SERVICE)).containsKey(date);
        assertThat(result.indiaVix()).containsKey(date);
    }

    private IndexPricesJson prices(String symbol, String indexName, String instrumentKey,
                                   LocalDate date, double close) {
        NSEIndexMasterData master = new NSEIndexMasterData();
        master.setSymbol(symbol);
        master.setIndexName(indexName);
        master.setInstrumentKey(instrumentKey);
        IndexPricesJson prices = new IndexPricesJson();
        prices.setNseIndexMasterData(master);
        prices.setTimeFrame(PriceFrequencey.DAILY);
        prices.setOhlcvData(List.of(new OHLCV(Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)),
                close, close, close, close, 0)));
        return prices;
    }
}
