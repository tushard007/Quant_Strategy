package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceDataServiceTest {
    private final PriceDataService service = new PriceDataService(null, null, null, null, null, null);

    @Test
    void normalizesByLocalTradingDateAndKeepsReplacementBar() {
        OHLCV first = bar(LocalDate.of(2026, 8, 21), 100);
        OHLCV replacement = bar(LocalDate.of(2026, 8, 21), 105);
        OHLCV earlier = bar(LocalDate.of(2026, 8, 20), 90);

        List<OHLCV> normalized = service.normalizeOhlcvData(List.of(first, replacement, earlier));

        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0).getClose()).isEqualTo(90);
        assertThat(normalized.get(1).getClose()).isEqualTo(105);
    }

    @Test
    void rejectsAnyCollectionWhoseRowAndUniqueDateCountsDiffer() {
        OHLCV first = bar(LocalDate.of(2026, 8, 21), 100);
        OHLCV duplicate = bar(LocalDate.of(2026, 8, 21), 105);

        assertThatThrownBy(() -> service.validateUniqueTradingDates(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match unique trading-date count");
    }

    private OHLCV bar(LocalDate date, double close) {
        return new OHLCV(Date.valueOf(date), close - 1, close + 1, close - 2, close, 1_000);
    }
}
