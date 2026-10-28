package com.ediary.backend.notification.service;

import com.ediary.backend.schedule.entity.*;
import com.ediary.backend.journal.entity.Homework;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationTextTest {
    private ScheduleEntry entry(long teacherId,String room) {
        var teacher=new com.ediary.backend.user.entity.TeacherProfile();teacher.setId(teacherId);teacher.setSurname("Коваленко");teacher.setName("Олександр");
        var subject=new Subject();subject.setTitle("Інформатика");subject.setId(1L);
        var entry=new ScheduleEntry();entry.setTeacher(teacher);entry.setSubject(subject);entry.setRoom(room);entry.setWeekday(Weekday.MONDAY);entry.setLessonNumber(3);entry.setStartTime(LocalTime.of(9,50));entry.setEndTime(LocalTime.of(10,35));entry.setEffectiveFrom(LocalDate.of(2026,11,2));return entry;
    }
    @Test void snapshotRetainsPreviousRoomAndFiltersUnrelatedTeachers() {
        var lesson=entry(1,"120");var before=ScheduleChangeText.capture(List.of(lesson));
        lesson.setRoom("205");var after=ScheduleChangeText.capture(List.of(lesson));
        String text=ScheduleChangeText.describe(before,after,1L,LocalDate.of(2026,10,28));
        assertThat(text).contains("3-й урок","09:50–10:35","Було:","Кабінет: 120","Стало:","Кабінет: 205","Діє з: 02.11.2026");
        assertThat(ScheduleChangeText.describe(before,after,2L,LocalDate.now())).isEmpty();
        assertThat(ScheduleChangeText.describe(after,after,null,LocalDate.now())).isEmpty();
    }
    @Test void additionsCancellationsAndTeacherReplacementAreExplicit() {
        var original=ScheduleChangeText.capture(List.of(entry(1,"120")));
        assertThat(ScheduleChangeText.describe(Map.of(),original,null,LocalDate.now())).contains("Додано урок");
        assertThat(ScheduleChangeText.describe(original,Map.of(),null,LocalDate.of(2026,10,28))).contains("Урок скасовано","28.10.2026");
        var replacement=entry(2,"120");replacement.getTeacher().setSurname("Бойко");
        var after=ScheduleChangeText.capture(List.of(replacement));
        assertThat(ScheduleChangeText.describe(original,after,1L,LocalDate.now())).contains("Коваленко","Бойко");
        assertThat(ScheduleChangeText.describe(original,after,2L,LocalDate.now())).contains("Коваленко","Бойко");
    }
    @Test void homeworkContainsLessonDateFullTaskAndExplicitMissingDeadline() {
        var lesson=new Lesson();lesson.setScheduleEntry(entry(1,"120"));lesson.setDate(LocalDate.of(2026,10,28));
        var homework=new Homework();homework.setLesson(lesson);homework.setDate(lesson.getDate());homework.setDescription("Завдання "+"А".repeat(900));
        String text=NotificationText.homework(homework);
        assertThat(text).contains("28.10.2026","Інформатика","Учитель: Коваленко Олександр","Без визначеного терміну",homework.getDescription());
        homework.setDeadline(LocalDate.of(2026,11,4));
        assertThat(NotificationText.homework(homework)).contains("Термін виконання: 04.11.2026");
    }
}
