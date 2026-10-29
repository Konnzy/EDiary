package com.ediary.backend.notification.service;

import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.journal.entity.Homework;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public final class NotificationText {
    private static final java.util.regex.Pattern LEGACY_ATTENDANCE = java.util.regex.Pattern.compile(
        "^(.+?) · (\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})(?: · (.*))?$");
    private static final java.util.regex.Pattern GRADE_NOTICE = java.util.regex.Pattern.compile(
        "^(Оцінка|Нова оцінка|Оцінку змінено|Оцінку видалено):\\s*(?:(?:з\\s+)?(\\d+)\\s*(?:→|на)\\s*)?(\\d+)(?:\\s+із\\s+12)?\\s*$");
    private static final java.util.regex.Pattern SCHEDULE_SLOT = java.util.regex.Pattern.compile(
        "^(понеділок|вівторок|середа|четвер|п[’']ятниця|субота|неділя), \\d+-й урок$",
        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private NotificationText() {}
    public static String concise(String category,String source) {
        if(source==null)return "";
        String[] lines=source.split("\\R");
        var kept=new java.util.LinkedHashSet<String>();
        for(String line:lines) {
            String value=line.trim();
            if(value.isEmpty())continue;
            if(category.equals("GRADE")&&(value.startsWith("Результат:")||value.startsWith("Результат видалено:"))) { kept.add(value);continue; }
            if(category.equals("GRADE")) {
                var score=GRADE_NOTICE.matcher(value);
                if(score.matches()) {
                    String label=switch(score.group(1)) {
                        case "Оцінку видалено" -> "Оцінку видалено";
                        case "Оцінку змінено" -> "Оцінку змінено";
                        default -> "Нова оцінка";
                    };
                    String previous=label.equals("Оцінку змінено")&&score.group(2)!=null?"з "+score.group(2)+" на ":"";
                    kept.add(label+": "+previous+score.group(3)+" із 12");
                    continue;
                }
            }
            boolean keep=switch(category) {
                case "GRADE" -> value.startsWith("Учень:")||value.startsWith("Предмет:")||value.startsWith("Тип роботи:")||value.startsWith("Оцінка:")||value.startsWith("Оцінку змінено:");
                case "HOMEWORK","HOMEWORK_DEADLINE" -> value.startsWith("Учень:")||value.startsWith("Предмет:")||value.startsWith("Термін виконання:");
                case "ABSENCE" -> value.startsWith("Учень:")||value.startsWith("Предмет:")||value.startsWith("Дата уроку:")||value.startsWith("Урок:")||value.startsWith("Попередню позначку")||value.startsWith("Статус:");
                case "MESSAGE" -> value.startsWith("Відправник:")||value.startsWith("Тема:");
                case "SCHEDULE","CLASS_SCHEDULE" -> !value.startsWith("Учень:")&&!value.startsWith("Навчальний рік:");
                case "PROLONGED_ABSENCE" -> !value.startsWith("Останній зафіксований")&&!value.startsWith("Предмет:")&&!value.startsWith("Дата уроку:")&&!value.startsWith("Урок:")&&!value.startsWith("Учитель:")&&!value.startsWith("Тема уроку:");
                default -> true;
            };
            if(category.equals("ABSENCE")&&value.startsWith("Учня позначено відсутнім")){kept.add("Статус: відсутній");continue;}
            if(keep)kept.add(value);
        }
        if(category.equals("HOMEWORK")||category.equals("HOMEWORK_DEADLINE")) {
            int start=source.indexOf("Завдання:\n");
            if(start>=0)kept.add("Завдання: "+clip(source.substring(start+10).replaceAll("\\s+"," ").trim(),180));
        }
        return kept.isEmpty()?clip(source,240):String.join("\n",kept);
    }
    public static String title(String category,String source,String fallback) {
        if(!category.equals("GRADE"))return fallback;
        return concise(category,source).lines().filter(line->GRADE_NOTICE.matcher(line).matches()||line.startsWith("Результат:")||line.startsWith("Результат видалено:")).findFirst().orElse(fallback);
    }
    public static String previewTitle(String category,String source,String fallback) {
        String full=title(category,source,fallback);
        var grade=GRADE_NOTICE.matcher(full);
        if(category.equals("GRADE")&&(full.startsWith("Результат:")||full.startsWith("Результат видалено:")))return full.substring(0,full.indexOf(':'));
        return category.equals("GRADE")&&grade.matches()?grade.group(1):full;
    }
    public static String preview(String category,String source) { return preview(category,source,false); }
    public static String preview(String category,String source,boolean parent) {
        String text=concise(category,source);
        var lines=text.lines().map(String::trim).filter(v->!v.isEmpty()).toList();
        java.util.function.Function<String,String> field=prefix->lines.stream().filter(v->v.startsWith(prefix)).map(v->v.substring(prefix.length()).trim()).findFirst().orElse("");
        String subject=field.apply("Предмет:"), child=field.apply("Учень:");
        if(child.isBlank()&&source!=null)child=source.lines().map(String::trim).filter(v->v.startsWith("Учень:"))
            .map(v->v.substring("Учень:".length()).trim()).findFirst().orElse("");
        String first="",second="";
        switch(category) {
            case "HOMEWORK","HOMEWORK_DEADLINE" -> {
                String deadline=field.apply("Термін виконання:");
                first=subject+(deadline.isEmpty()?"":" · "+(deadline.equals("Без визначеного терміну")?"без терміну":"до "+deadline));
                second=field.apply("Завдання:");
            }
            case "GRADE" -> {
                String score=lines.stream().filter(line->GRADE_NOTICE.matcher(line).matches()||line.startsWith("Результат:")||line.startsWith("Результат видалено:"))
                    .map(line->line.substring(line.indexOf(':')+1).trim()).findFirst().orElse("");
                first=subject+(score.isBlank()?"":(subject.isBlank()?"":" · ")+score);
                second=field.apply("Тип роботи:");
            }
            case "ABSENCE" -> {
                String date=field.apply("Дата уроку:");
                String description=lines.stream().filter(v->v.startsWith("Статус:")||v.startsWith("Попередню позначку")).findFirst().orElse("");
                if(subject.isBlank()&&source!=null) {
                    String[] legacyLines=source.strip().split("\\R",2);
                    var legacy=LEGACY_ATTENDANCE.matcher(legacyLines[0]);
                    if(legacy.matches()) {
                        subject=legacy.group(1);date=legacy.group(2);
                        int childSeparator=subject.lastIndexOf(" · ");
                        if(childSeparator>=0) { child=subject.substring(0,childSeparator);subject=subject.substring(childSeparator+3); }
                        description=legacyLines.length>1?legacyLines[1]:legacy.group(3);
                    }
                }
                if(date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    try { date=date(LocalDate.parse(date)); }
                    catch(java.time.format.DateTimeParseException ignored) { /* Keep malformed historic dates readable. */ }
                }
                first=subject+(date.isEmpty()?"":" · "+date);
                second=attendanceSummary(description);
                if(second.isBlank())second=field.apply("Урок:");
            }
            case "MESSAGE" -> {
                first=field.apply("Відправник:");second=field.apply("Тема:");
                if(first.isBlank()&&second.isBlank()) {
                    // Older notices stored sender and subject on one line.
                    String legacy=source==null?"":source.strip();
                    String[] separateLines=legacy.split("\\R",2);
                    int separator=legacy.indexOf(" · ");
                    if(separateLines.length==2) { first=separateLines[0];second=separateLines[1]; }
                    else if(separator>=0) { first=legacy.substring(0,separator);second=legacy.substring(separator+3); }
                    else { first=lines.isEmpty()?"":lines.getFirst();second=lines.size()>1?lines.get(1):""; }
                }
            }
            case "SCHEDULE","CLASS_SCHEDULE" -> {
                String schoolClass=field.apply("Клас:");
                first=schoolClass.isBlank()?"":"Клас "+schoolClass;
                String week=field.apply("Навчальний тиждень:");
                if(!week.isEmpty())first+=(first.isBlank()?"":" · ")+week+"-й тиждень";
                var slots=lines.stream().filter(v->SCHEDULE_SLOT.matcher(v).matches()).toList();
                if(slots.size()>1)second="Змінено уроків: "+slots.size();
                else if(slots.size()==1) {
                    String slot=slots.getFirst();
                    second=Character.toUpperCase(slot.charAt(0))+slot.substring(1)+": "+scheduleAction(lines,lines.indexOf(slot)+1);
                } else {
                    // Already compact historic previews do not contain a separate slot line.
                    if(first.isBlank())first=lines.isEmpty()?"":lines.getFirst();
                    second=lines.stream().filter(v->!v.equals(lines.getFirst())&&!v.startsWith("Клас:")&&!v.startsWith("Навчальний тиждень:")).findFirst().orElse("");
                }
            }
            case "CLASS_ROSTER" -> {
                String schoolClass=field.apply("Клас:");
                first=child+(schoolClass.isBlank()?"":" · "+schoolClass);
                String from=field.apply("Було:"),to=field.apply("Стало:");
                second=!from.isBlank()&&!to.isBlank()?"Переведено: "+from+" → "+to:field.apply("Дія:");
                if(!second.isBlank())second=Character.toUpperCase(second.charAt(0))+second.substring(1);
                if(first.isBlank()&&second.isBlank()) {
                    first=lines.isEmpty()?"":lines.getFirst();
                    second=lines.size()>1?lines.get(1):"";
                }
            }
            case "PROLONGED_ABSENCE" -> {
                String name=child.isBlank()&&!lines.isEmpty()?lines.getFirst():child;
                String schoolClass=field.apply("Клас:");
                first=name+(schoolClass.isBlank()?"":" · "+schoolClass);
                String count=field.apply("Пропущено уроків поспіль:");
                second=count.isBlank()?field.apply("Період:"):"Пропущено уроків поспіль: "+count;
            }
            default -> {var meaningful=lines.stream().filter(v->!v.startsWith("Учень:")&&!v.startsWith("Клас:")).toList();first=meaningful.isEmpty()?"":meaningful.getFirst();second=meaningful.size()>1?meaningful.get(1):"";}
        }
        String shortName=Stream.of(child.split("\\s+")).limit(2).collect(Collectors.joining(" "));
        if(parent&&!shortName.isBlank()&&!category.equals("MESSAGE"))second=shortName+(second.isBlank()?"":": "+second);
        if(first.isBlank())first=lines.stream().filter(v->!v.startsWith("Учень:")&&!v.startsWith("Клас:")).findFirst().orElse(text);
        return clip(first.replaceAll("\\s+"," ").trim(),110)+(second.isBlank()?"":"\n"+clip(second.replaceAll("\\s+"," ").trim(),110));
    }
    private static String scheduleAction(java.util.List<String> lines,int start) {
        var changes=lines.subList(start,lines.size()).stream()
            .filter(v->!v.startsWith("Діє з:")&&!v.startsWith("Скасовано з:")).toList();
        if(changes.stream().anyMatch(v->v.startsWith("Додано")))return "додано урок";
        if(changes.stream().anyMatch(v->v.startsWith("Скасовано")))return "урок скасовано";
        var fields=changes.stream().filter(v->v.contains("→")).toList();
        if(fields.size()!=1)return "оновлено урок";
        String change=fields.getFirst();
        if(change.startsWith("Учитель:"))return "замінено вчителя";
        if(change.startsWith("Предмет:"))return "змінено предмет";
        if(change.startsWith("Час:"))return "час "+change.substring("Час:".length()).trim();
        if(change.startsWith("Кабінет:"))return "кабінет "+change.substring("Кабінет:".length()).trim();
        return "оновлено урок";
    }
    private static String attendanceSummary(String description) {
        if(description==null)return "";
        for(String status:java.util.List.of("Учень присутній.","Відсутність із поважної причини.",
            "Учня звільнено від заняття.","Учень прибув із запізненням.")) {
            if(description.contains(status))return status;
        }
        if(description.contains("Попередню позначку")||description.contains("Попередня позначка")
            ||description.contains("Позначку відсутності скасовано"))return "Позначку відсутності скасовано.";
        if(description.equals("Статус: відсутній")||description.contains("Учень відсутній."))return "Учень відсутній.";
        return description;
    }
    private static String clip(String value,int max) { return value.length()<=max?value:value.substring(0,max-1).stripTrailing()+"…"; }
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
