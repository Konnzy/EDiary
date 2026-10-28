package com.ediary.backend.notification.service;

import com.ediary.backend.schedule.entity.ScheduleEntry;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ScheduleChangeText {
    private ScheduleChangeText() {}
    public record Entry(String slot, String description, Long teacherId, LocalDate effectiveFrom) {}
    public static Map<String,Entry> capture(List<ScheduleEntry> entries) {
        Map<String,Entry> result=new TreeMap<>();
        for(var e:entries) {
            var teacher=e.getTeacher();
            String name=Stream.of(teacher.getSurname(),teacher.getName(),teacher.getPatronymic()).filter(v->v!=null&&!v.isBlank()).collect(Collectors.joining(" "));
            String key=e.getWeekday().ordinal()+"-"+e.getLessonNumber();
            String slot=DayOfWeek.valueOf(e.getWeekday().name()).getDisplayName(TextStyle.FULL,Locale.forLanguageTag("uk-UA"))+", "+e.getLessonNumber()+"-й урок";
            String description=e.getSubject().getTitle()+" · "+e.getStartTime()+"–"+e.getEndTime()+"\nУчитель: "+name+"\nКабінет: "+(e.getRoom()==null||e.getRoom().isBlank()?"Не вказано":e.getRoom());
            result.put(key,new Entry(slot,description,teacher.getId(),e.getEffectiveFrom()));
        }
        return result;
    }
    public static String describe(Map<String,Entry> before,Map<String,Entry> after,Long teacherId,LocalDate date) {
        var keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());
        var changes=new ArrayList<String>();
        for(String key:keys) {
            var old=before.get(key);var next=after.get(key);
            if(old!=null&&next!=null&&old.description().equals(next.description())&&Objects.equals(old.teacherId(),next.teacherId()))continue;
            if(teacherId!=null && (old==null||!teacherId.equals(old.teacherId())) && (next==null||!teacherId.equals(next.teacherId())))continue;
            String change=(next==null?old:next).slot()+"\n";
            if(old==null)change+="Додано урок:\n"+next.description();
            else if(next==null)change+="Урок скасовано.\nБуло: "+old.description();
            else change+="Було: "+old.description()+"\nСтало: "+next.description();
            change+="\n"+(next==null?"Скасовано з: "+NotificationText.date(date):"Діє з: "+NotificationText.date(next.effectiveFrom()==null?date:next.effectiveFrom()));
            changes.add(change);
        }
        return String.join("\n\n",changes);
    }
}
