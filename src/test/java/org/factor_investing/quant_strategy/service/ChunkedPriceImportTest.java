package org.factor_investing.quant_strategy.service;

import com.upstox.api.GetHistoricalCandleResponse;
import com.upstox.api.HistoricalCandleData;
import org.factor_investing.quant_strategy.model.*;
import org.factor_investing.quant_strategy.repository.*;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkedPriceImportTest {
    final StockDataRepository stocks = mock(StockDataRepository.class);
    final ETFPriceDataRepository etfs = mock(ETFPriceDataRepository.class);
    final NSE_StockDataService masters = mock(NSE_StockDataService.class);
    final UpstoxHistoricalDataService upstream = mock(UpstoxHistoricalDataService.class);
    final PriceImportPersistence persistence = mock(PriceImportPersistence.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final PriceDataService service = new PriceDataService(stocks, etfs, null, null, masters, upstream, events, persistence);

    ChunkedPriceImportTest() {
        when(persistence.acquireLock(any(), any())).thenReturn(mock(PriceImportPersistence.ImportLock.class));
        ReflectionTestUtils.setField(service, "importChunkSize", 2);
        ReflectionTestUtils.setField(service, "importRequestDelayMs", 0L);
    }

    @Test
    void commitsFirstChunkBeforeFetchingNextAndSavesRemainder() throws Exception {
        when(masters.getAllStockData()).thenReturn(List.of(stock("A"), stock("B"), stock("C")));
        when(stocks.findAllByTimeFrameAndNseStockMasterData_SymbolIn(any(), anyList())).thenReturn(List.of());
        when(upstream.getHistoricalCandleData(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(response());
        service.saveOrUpdateStockPriceData(PriceFrequencey.WEEKLY);
        var order = inOrder(upstream, persistence);
        order.verify(upstream).getHistoricalCandleData(eq("NSE_EQ|A"), anyString(), anyInt(), anyString(), anyString());
        order.verify(upstream).getHistoricalCandleData(eq("NSE_EQ|B"), anyString(), anyInt(), anyString(), anyString());
        order.verify(persistence).saveStocks(argThat(rows -> rows.size() == 2));
        order.verify(upstream).getHistoricalCandleData(eq("NSE_EQ|C"), anyString(), anyInt(), anyString(), anyString());
        order.verify(persistence).saveStocks(argThat(rows -> rows.size() == 1));
        verify(events).publishEvent(new PriceImportProgress(3, 3, 3, List.of()));
    }

    @Test
    void processes225SymbolsAcrossNineCommittedChunks() throws Exception {
        ReflectionTestUtils.setField(service, "importChunkSize", 25);
        when(masters.getAllStockData()).thenReturn(java.util.stream.IntStream.range(0, 225)
                .mapToObj(i -> stock("S" + i)).toList());
        when(stocks.findAllByTimeFrameAndNseStockMasterData_SymbolIn(any(), anyList())).thenReturn(List.of());
        when(upstream.getHistoricalCandleData(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(response());
        assertThat(service.saveOrUpdateStockPriceData(PriceFrequencey.WEEKLY)).contains("225");
        verify(persistence, times(9)).saveStocks(argThat(rows -> rows.size() == 25));
        verify(events).publishEvent(new PriceImportProgress(225, 225, 225, List.of()));
    }

    @Test
    void laterCommitFailureDoesNotUndoEarlierCommitOrReportSuccess() {
        when(masters.getAllStockData()).thenReturn(List.of(stock("A"), stock("B"), stock("C")));
        when(stocks.findAllByTimeFrameAndNseStockMasterData_SymbolIn(any(), anyList())).thenReturn(List.of());
        when(upstream.getHistoricalCandleData(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(response());
        doNothing().doThrow(new IllegalStateException("database unavailable")).when(persistence).saveStocks(anyList());
        assertThatThrownBy(() -> service.saveOrUpdateStockPriceData(PriceFrequencey.WEEKLY))
                .hasMessage("database unavailable");
        verify(persistence, times(2)).saveStocks(anyList());
        verify(events).publishEvent(new PriceImportProgress(2, 3, 2, List.of()));
        verify(events, never()).publishEvent(new PriceImportProgress(3, 3, 3, List.of()));
    }

    @Test
    void etfsFetchOnlyFromLatestStoredBarAndMergeWithoutDuplicates() throws Exception {
        NSE_ETFMasterData master = new NSE_ETFMasterData();
        master.setSymbol("ETF"); master.setIsinNumber("ETF"); master.setSecurityName("ETF");
        ETFPricesJson existing = new ETFPricesJson();
        existing.setNseETFMasterData(master);
        existing.setTimeFrame(PriceFrequencey.DAILY);
        existing.setOhlcvData(List.of(bar("2026-08-21", 90)));
        when(upstream.getNSEIndexData()).thenReturn(List.of(master));
        when(etfs.findAllByTimeFrameAndNseETFMasterData_SymbolIn(any(), anyList())).thenReturn(List.of(existing));
        when(upstream.getHistoricalCandleData(anyString(), anyString(), anyInt(), anyString(), eq("2026-08-21")))
                .thenReturn(response());
        service.saveOrUpdateETFPriceData(PriceFrequencey.DAILY);
        verify(persistence).saveEtfs(argThat(rows -> rows.size() == 1
                && rows.getFirst().getOhlcvData().size() == 1
                && rows.getFirst().getOhlcvData().getFirst().getClose() == 105));
    }

    @Test
    void weeklyFetchRefreshesEntireLatestWeek() {
        assertThat(service.incrementalStart(List.of(bar("2026-08-21", 100)), PriceFrequencey.WEEKLY))
                .isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    void failedHistoricalRangeDoesNotSaveLaterRangesAsComplete() {
        when(masters.getAllStockData()).thenReturn(List.of(stock("A")));
        when(stocks.findAllByTimeFrameAndNseStockMasterData_SymbolIn(any(), anyList())).thenReturn(List.of());
        // Non-null response with missing data is an invalid range, not a successful empty range.
        when(upstream.getHistoricalCandleData(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(new GetHistoricalCandleResponse());
        assertThatThrownBy(() -> service.saveOrUpdateStockPriceData(PriceFrequencey.DAILY))
                .hasMessageContaining("failed symbols=[A]");
        verify(persistence, never()).saveStocks(anyList());
        verify(events).publishEvent(new PriceImportProgress(1, 1, 0, List.of("A")));
    }

    private NSEStockMasterData stock(String symbol) {
        NSEStockMasterData master = new NSEStockMasterData();
        master.setSymbol(symbol); master.setIsinNumber(symbol); master.setNameOfCompany(symbol);
        return master;
    }

    private OHLCV bar(String date, double close) {
        return new OHLCV(Date.valueOf(date), close, close, close, close, 100);
    }

    private GetHistoricalCandleResponse response() {
        GetHistoricalCandleResponse response = new GetHistoricalCandleResponse();
        HistoricalCandleData data = new HistoricalCandleData();
        ReflectionTestUtils.setField(data, "candles", new ArrayList<>(List.of(new ArrayList<>(List.of(
                "2026-08-21T00:00:00+05:30", 100, 110, 90, 105, 1000)))));
        response.setData(data);
        return response;
    }
}
