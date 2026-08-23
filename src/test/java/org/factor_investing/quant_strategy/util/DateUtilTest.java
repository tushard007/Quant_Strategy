package org.factor_investing.quant_strategy.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateUtilTest {

    @Test
    void findsExactDateWhenAvailable() {
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14));

        assertEquals(LocalDate.of(2026, 8, 14),
                DateUtil.findNearestPastDate(dates, LocalDate.of(2026, 8, 14)));
    }

    @Test
    void fallsBackToNearestEarlierDate() {
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 17));

        assertEquals(LocalDate.of(2026, 8, 13),
                DateUtil.findNearestPastDate(dates, LocalDate.of(2026, 8, 14)));
    }

    @Test
    void doesNotUseAFutureDate() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 8, 17));

        assertNull(DateUtil.findNearestPastDate(dates, LocalDate.of(2026, 8, 14)));
    }
}
