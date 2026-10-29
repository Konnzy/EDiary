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
        assertThat(text).contains("3-й урок","Кабінет: 120 → 205","Діє з: 02.11.2026").doesNotContain("Учитель:","09:50","Предмет:");
        assertThat(ScheduleChangeText.describe(before,after,2L,LocalDate.now())).isEmpty();
        assertThat(ScheduleChangeText.describe(after,after,null,LocalDate.now())).isEmpty();
    }
    @Test void additionsCancellationsAndTeacherReplacementAreExplicit() {
        var original=ScheduleChangeText.capture(List.of(entry(1,"120")));
        assertThat(ScheduleChangeText.describe(Map.of(),original,null,LocalDate.now())).contains("Додано:");
        assertThat(ScheduleChangeText.describe(original,Map.of(),null,LocalDate.of(2026,10,28))).contains("Скасовано:","28.10.2026");
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
    @Test void conciseGradesKeepScoreAndDoNotRepeatLessonMetadata() {
        String body="Учень: Іванов Валерій\nКлас: 9-А\nПредмет: Інформатика\nДата уроку: 12.10.2026\nУрок: 6\nУчитель: Коваленко\nТема уроку: Повторення\nТип роботи: Самостійна робота\nОцінка: 7\nКоментар учителя: Добре";
        String result=NotificationText.concise("GRADE",body);
        assertThat(result).contains("Учень:","Предмет:","Тип роботи:","Нова оцінка: 7 із 12").doesNotContain("Клас:","Урок:","Учитель:","Коментар", "Дата уроку");
        assertThat(NotificationText.preview("GRADE",body)).isEqualTo("Інформатика · 7 із 12\nСамостійна робота");
        assertThat(NotificationText.concise("GRADE","Оцінку змінено: 7 → 11")).isEqualTo("Оцінку змінено: з 7 на 11 із 12");
        assertThat(NotificationText.title("GRADE",body,"Нова оцінка")).isEqualTo("Нова оцінка: 7 із 12");
        assertThat(NotificationText.title("GRADE","Оцінку змінено: 7 → 11 із 12","Оцінку змінено")).isEqualTo("Оцінку змінено: з 7 на 11 із 12");
        assertThat(NotificationText.title("GRADE","Оцінку видалено: 11 із 12","Оцінку видалено")).isEqualTo("Оцінку видалено: 11 із 12");
    }
    @Test void gradeBellUsesShortHeadingAndKeepsScoreWithSubject() {
        for(String notice:List.of("Нова оцінка: 11 із 12","Оцінку змінено: з 9 на 11 із 12","Оцінку видалено: 11 із 12")) {
            String body="Предмет: Інформатика\nТип роботи: Класна робота\n"+notice;
            assertThat(NotificationText.title("GRADE",body,notice)).isEqualTo(notice);
            assertThat(NotificationText.previewTitle("GRADE",body,notice)).isEqualTo(notice.substring(0,notice.indexOf(':')));
            assertThat(NotificationText.preview("GRADE",body)).isEqualTo("Інформатика · "+notice.substring(notice.indexOf(':')+2)+"\nКласна робота");
        }
    }
    @Test void homeworkPreviewPrioritizesSubjectDeadlineAndTaskInsteadOfStudentMetadata() {
        String body="Учень: Іванов Валерій Миколайович\nКлас: 9-А\nПредмет: Інформатика\nТермін виконання: 29.10.2026\nЗавдання:\nВправи 4–6, підготувати приклади.";
        assertThat(NotificationText.preview("HOMEWORK",body)).isEqualTo("Інформатика · до 29.10.2026\nВправи 4–6, підготувати приклади.");
        assertThat(NotificationText.preview("HOMEWORK_DEADLINE",body,true)).isEqualTo("Інформатика · до 29.10.2026\nІванов Валерій: Вправи 4–6, підготувати приклади.");
        assertThat(NotificationText.preview("HOMEWORK",body+"А".repeat(1000)).lines().count()).isEqualTo(2);
    }
    @Test void legacyPreviewsRemainReadableWithoutStructuredLabels() {
        assertThat(NotificationText.preview("SCHEDULE","9-А клас · 2-й тиждень")).contains("9-А", "2-й тиждень");
        assertThat(NotificationText.preview("ABSENCE","Інформатика · 28.10.2026")).contains("Інформатика", "28.10.2026");
    }
    @Test void schedulePreviewSummarizesTeacherChangesWithoutLongNames() {
        String context="Клас: 9-А\nНавчальний рік: 2026/2027\nНавчальний тиждень: 2\n\n";
        String body=context+"вівторок, 8-й урок\nУчитель: Коваленко Олександр Іванович → Бондаренко Андрій Олегович\nДіє з: 02.11.2026";
        String expected="Клас 9-А · 2-й тиждень\nВівторок, 8-й урок: замінено вчителя";
        for(String category:List.of("SCHEDULE","CLASS_SCHEDULE")) {
            assertThat(NotificationText.preview(category,body)).isEqualTo(expected);
            assertThat(NotificationText.preview(category,expected)).isEqualTo(expected);
            assertThat(NotificationText.preview(category,"Учень: Іванов Валерій Миколайович\n"+body,true))
                .isEqualTo("Клас 9-А · 2-й тиждень\nІванов Валерій: Вівторок, 8-й урок: замінено вчителя");
        }
        assertThat(NotificationText.concise("SCHEDULE",body)).contains("Коваленко Олександр Іванович → Бондаренко Андрій Олегович","02.11.2026");
    }
    @Test void schedulePreviewHandlesEachChangeAndMultipleLessons() {
        String context="Клас: 9-А\nНавчальний тиждень: 1\nпонеділок, 3-й урок\n";
        var cases=Map.of(
            "Додано: Інформатика · 09:50–10:35","додано урок",
            "Скасовано: Інформатика · 09:50–10:35","урок скасовано",
            "Предмет: Математика → Інформатика","змінено предмет",
            "Кабінет: 120 → 205","кабінет 120 → 205",
            "Час: 09:50–10:35 → 10:45–11:30","час 09:50–10:35 → 10:45–11:30",
            "Учитель: Коваленко → Бойко\nКабінет: 120 → 205","оновлено урок");
        cases.forEach((change,summary)->assertThat(NotificationText.preview("SCHEDULE",context+change+"\nДіє з: 02.11.2026"))
            .isEqualTo("Клас 9-А · 1-й тиждень\nПонеділок, 3-й урок: "+summary));
        assertThat(NotificationText.preview("SCHEDULE",context+"Додано: Інформатика\n\nп’ятниця, 4-й урок\nСкасовано: Фізика"))
            .isEqualTo("Клас 9-А · 1-й тиждень\nЗмінено уроків: 2");
        assertThat(NotificationText.preview("SCHEDULE","Клас: 9-А\nНавчальний тиждень: 1"))
            .isEqualTo("Клас 9-А · 1-й тиждень");
        assertThat(NotificationText.preview("SCHEDULE",null)).isEmpty();
    }
    @Test void classRosterPreviewKeepsStudentAndAssignmentOrTransfer() {
        String context="Учень: Іванов Валерій\n";
        for(String action:List.of("додано до класу","вилучено зі складу класу")) {
            String preview=NotificationText.preview("CLASS_ROSTER",context+"Дія: "+action+"\nКлас: 9-А");
            assertThat(preview).isEqualTo("Іванов Валерій · 9-А\n"+Character.toUpperCase(action.charAt(0))+action.substring(1));
            assertThat(NotificationText.preview("CLASS_ROSTER",preview)).isEqualTo(preview);
        }
        assertThat(NotificationText.preview("CLASS_ROSTER",context+"Дія: переведення\nБуло: 9-А\nСтало: 9-Б"))
            .isEqualTo("Іванов Валерій\nПереведено: 9-А → 9-Б");
    }
    @Test void prolongedAbsencePreviewKeepsStudentClassAndCount() {
        String body="Іванов Валерій\nКлас: 9-А\nПропущено уроків поспіль: 3\nПеріод: 05.10.2026 – 06.10.2026\nОстанній зафіксований пропуск:\nПредмет: Інформатика\nДата уроку: 06.10.2026";
        String expected="Іванов Валерій · 9-А\nПропущено уроків поспіль: 3";
        assertThat(NotificationText.preview("PROLONGED_ABSENCE",body)).isEqualTo(expected);
        assertThat(NotificationText.preview("PROLONGED_ABSENCE",expected)).isEqualTo(expected);
    }
    @Test void messagePreviewSeparatesSenderAndSubjectForNewAndLegacyNotices() {
        String name="Коваленко Олександр Іванович", subject="Підготовка до наступного уроку · матеріали";
        String expected=name+"\n"+subject;
        assertThat(NotificationText.preview("MESSAGE","Відправник: "+name+"\nТема: "+subject+"\n\nПовний текст у застосунку.")).isEqualTo(expected);
        assertThat(NotificationText.preview("MESSAGE",name+" · "+subject)).isEqualTo(expected);
        assertThat(NotificationText.preview("MESSAGE",expected,true)).isEqualTo(expected);
        String longPreview=NotificationText.preview("MESSAGE",name.repeat(10)+" · "+subject.repeat(10));
        assertThat(longPreview.lines().toList()).hasSize(2).allMatch(line->line.length()<=110);
    }
    @Test void attendanceCorrectionSeparatesLegacyContextAndUsesUkrainianDate() {
        String correction="Попередня позначка відсутності більше не актуальна.";
        assertThat(NotificationText.preview("ABSENCE","Інформатика · 2026-10-06 · "+correction))
            .isEqualTo("Інформатика · 06.10.2026\nПозначку відсутності скасовано.");
        String legacy="Іванов Валерій · Інформатика · 06.10.2026 · Учень присутній. "+correction;
        assertThat(NotificationText.preview("ABSENCE",legacy)).isEqualTo("Інформатика · 06.10.2026\nУчень присутній.");
        assertThat(NotificationText.preview("ABSENCE",legacy,true)).isEqualTo("Інформатика · 06.10.2026\nІванов Валерій: Учень присутній.");
        assertThat(NotificationText.preview("ABSENCE","Інформатика · 2026-10-06 · Учень відсутній. · "+correction))
            .endsWith("\nПозначку відсутності скасовано.");
    }
    @Test void structuredAttendanceUsesActualStatusWithoutCorrectionBoilerplate() {
        String context="Учень: Іванов Валерій Миколайович\nПредмет: Інформатика\nДата уроку: 06.10.2026\n";
        for(String status:List.of("Учень присутній.","Відсутність із поважної причини.","Учня звільнено від заняття.","Учень прибув із запізненням.","Позначку відсутності скасовано.")) {
            String expected="Інформатика · 06.10.2026\n"+status;
            assertThat(NotificationText.preview("ABSENCE",context+"Попередню позначку відсутності скасовано. "+status)).isEqualTo(expected);
            assertThat(NotificationText.preview("ABSENCE",expected)).isEqualTo(expected);
        }
        assertThat(NotificationText.preview("ABSENCE",context+"Учня позначено відсутнім на цьому уроці."))
            .isEqualTo("Інформатика · 06.10.2026\nУчень відсутній.");
        assertThat(NotificationText.preview("ABSENCE","Інформатика · 2026-99-06 · Учень присутній."))
            .isEqualTo("Інформатика · 2026-99-06\nУчень присутній.");
    }
}
