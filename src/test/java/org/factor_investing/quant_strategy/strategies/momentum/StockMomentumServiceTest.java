package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StockMomentumServiceTest {
    private final StockMomentumService service = new StockMomentumService();

    @Test
    void calculatesReturnsFromExactTradingBarOffsets() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<OHLCV> bars = bars(start, 253);

        StockMomentum result = service.calculateStockMomentum("TEST", bars, start.plusDays(252));

        assertThat(result).isNotNull();
        assertThat(result.getOneYearReturn()).isCloseTo(252.0f, within(.001f));
        assertThat(result.getSixMonthReturn()).isCloseTo(55.7522f, within(.001f));
        assertThat(result.getThreeMonthReturn()).isCloseTo(21.7993f, within(.001f));
    }

    @Test
    void excludesBarsAfterAsOfDateBeforeApplyingOffsets() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<OHLCV> bars = bars(start, 254);
        bars.get(253).setClose(10_000);

        StockMomentum result = service.calculateStockMomentum("TEST", bars, start.plusDays(252));

        assertThat(result.getOneYearReturn()).isCloseTo(252.0f, within(.001f));
    }

    @Test
    void requiresCurrentBarPlus252HistoricalBars() {
        assertThat(service.calculateStockMomentum("TEST", bars(LocalDate.of(2025, 1, 1), 252), LocalDate.of(2026, 1, 1))).isNull();
    }

    @Test
    void doesNotQualifyNegativeTwelveMonthMomentumWithPositiveSixAndThreeMonthMomentum() {
        StockMomentum momentum = new StockMomentum("RECOVERY", -10.0f, 15.0f, 8.0f, LocalDate.of(2026, 1, 1));

        assertThat(momentum.isQualifiesForMomentum()).isFalse();
    }

    private List<OHLCV> bars(LocalDate start, int count) {
        List<OHLCV> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double close = 100 + index;
            result.add(new OHLCV(Date.valueOf(start.plusDays(index)), close - 1, close + 1, close - 2, close, 1_000 + index));
        }
        return result;
    }
}
