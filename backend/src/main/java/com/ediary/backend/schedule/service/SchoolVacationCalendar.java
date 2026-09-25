package com.ediary.backend.schedule.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;

public final class SchoolVacationCalendar {

    private SchoolVacationCalendar() {
    }

    public static boolean isVacation(LocalDate date) {
        int startYear = date.getMonthValue() >= Month.SEPTEMBER.getValue()
            ? date.getYear() : date.getYear() - 1;
        LocalDate autumn = lastWeekStart(startYear, Month.OCTOBER);
        LocalDate winter = lastWeekStart(startYear, Month.DECEMBER);
        LocalDate spring = lastWeekStart(startYear + 1, Month.MARCH);
        LocalDate summerStart = LocalDate.of(startYear + 1, Month.JUNE, 1);
        LocalDate summerEnd = LocalDate.of(startYear + 1, Month.AUGUST, 31);
        return within(date, autumn, autumn.plusDays(6))
            || within(date, winter, winter.plusDays(13))
            || within(date, spring, spring.plusDays(6))
            || within(date, summerStart, summerEnd);
    }

    private static LocalDate lastWeekStart(int year, Month month) {
        return YearMonth.of(year, month).atEndOfMonth()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static boolean within(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
