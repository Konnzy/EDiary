package com.ediary.backend.schedule.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

final class SchedulePeriod {

    private SchedulePeriod() {
    }

    static String academicYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    static int weekNumber(LocalDate date) {
        int startYear = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        LocalDate firstCycleMonday = LocalDate.of(startYear, 9, 1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long elapsedWeeks = ChronoUnit.WEEKS.between(firstCycleMonday, currentMonday);
        return Math.floorMod(elapsedWeeks, 2) + 1;
    }
}
