package com.ediary.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SchoolVacationCalendarTest {

    @Test
    void coversTheLastFullWeekOfOctober() {
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 10, 25))).isFalse();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 10, 26))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 11, 2))).isFalse();
    }

    @Test
    void coversTwoConsecutiveWinterWeeks() {
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 12, 27))).isFalse();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2026, 12, 28))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 1, 10))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 1, 11))).isFalse();
    }

    @Test
    void coversSpringAndSummerBoundaries() {
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 3, 28))).isFalse();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 3, 29))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 4, 4))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 4, 5))).isFalse();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 6, 1))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 8, 31))).isTrue();
        assertThat(SchoolVacationCalendar.isVacation(LocalDate.of(2027, 9, 1))).isFalse();
    }
}
