package org.factor_investing.quant_strategy.util;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    public static LocalDate getStartMonthDate(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        while (start.getDayOfWeek() == DayOfWeek.SATURDAY || start.getDayOfWeek() == DayOfWeek.SUNDAY) {
            start = start.plusDays(1);
        }
        return start;
    }

    public static LocalDate getEndMonthDate(int year, int month) {
        LocalDate end = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        while (end.getDayOfWeek() == DayOfWeek.SATURDAY || end.getDayOfWeek() == DayOfWeek.SUNDAY) {
            end = end.minusDays(1);
        }
        return end;
    }

    public static DayOfWeek getDayOfWeek(LocalDate date) {
        return date.getDayOfWeek();
    }

    public static String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return date.format(formatter);
    }

    public static LocalDate getDateBeforeMonth(LocalDate date, int month) {
        return date.minusMonths(month);
    }

    public static LocalDate getDateBeforeYear(LocalDate date, int year) {
        return date.minusYears(year);
    }
}
