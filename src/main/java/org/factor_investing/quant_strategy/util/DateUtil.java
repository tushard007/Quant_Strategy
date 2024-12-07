package org.factor_investing.quant_strategy.util;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.*;
import java.util.HashSet;

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

    public static LocalDate convertDateToLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static Date convertLocalDateToDate(LocalDate localDate) throws ParseException {
        return  new SimpleDateFormat("yyyy-MM-dd").parse(localDate.toString());
    }

    public static LocalDate findNearestDate(Set<LocalDate> dateSet, LocalDate inputDate) {
        LocalDate nearestDate = null;
        long minDifference = Long.MAX_VALUE;
        // Enhanced for-loop to iterate through the Set
        for (LocalDate date : dateSet) {
            long difference = Math.abs(ChronoUnit.DAYS.between(inputDate, date));
            if (difference < minDifference) {
                minDifference = difference;
                nearestDate = date;
            }
        }
        return nearestDate;
    }
    public static Set<LocalDate> convertToLocalDateSet(Set<Date> dateSet) {
        Set<LocalDate> localDateSet = new HashSet<>();
        for (Date date : dateSet) {
            LocalDate localDate = Instant.ofEpochMilli(date.getTime()).
                    atZone(ZoneId.systemDefault()).toLocalDate();
            localDateSet.add(localDate);
        }
        return localDateSet;
    }
}
