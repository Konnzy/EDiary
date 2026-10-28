package com.ediary.backend.notification.service;

import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.journal.entity.Homework;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public final class NotificationText {
    private NotificationText() {}
    public static String date(LocalDate date) { return date == null ? "Без визначеного терміну" : date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
    public static String lesson(Lesson lesson) {
        var entry=lesson.getScheduleEntry();
        var teacher=entry.getTeacher();
        String teacherName=teacher==null?"Не призначено":Stream.of(teacher.getSurname(),teacher.getName(),teacher.getPatronymic()).filter(v->v!=null&&!v.isBlank()).collect(Collectors.joining(" "));
        return "Предмет: "+entry.getSubject().getTitle()+"\nДата уроку: "+date(lesson.getDate())
            +"\nУрок: "+entry.getLessonNumber()
            +(entry.getStartTime()==null?"":" · "+entry.getStartTime()+"–"+entry.getEndTime())
            +"\nУчитель: "+teacherName+(lesson.getTopic()==null||lesson.getTopic().isBlank()?"":"\nТема уроку: "+lesson.getTopic());
    }
    public static String homework(Homework homework) {
        return lesson(homework.getLesson())+"\nДата видачі: "+date(homework.getDate())
            +"\nТермін виконання: "+date(homework.getDeadline())+"\n\nЗавдання:\n"+homework.getDescription();
    }
    public static String workType(com.ediary.backend.journal.entity.LessonWorkType type) {
        if(type==null)return "Не вказано";
        return switch(type.name()) {
            case "CLASSWORK" -> "Класна робота"; case "HOMEWORK" -> "Домашня робота";
            case "INDEPENDENT" -> "Самостійна робота"; case "CONTROL" -> "Контрольна робота";
            case "PRACTICAL" -> "Практична робота"; case "LABORATORY" -> "Лабораторна робота";
            case "THEMATIC" -> "Тематична"; case "SEMESTER" -> "Семестрова"; case "YEAR" -> "Річна";
            default -> type.name();
        };
    }
}
