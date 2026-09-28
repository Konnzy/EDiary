package com.ediary.backend.journal.service;

import com.ediary.backend.journal.dto.TeacherDashboardResponse;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.service.SchedulePeriod;
import com.ediary.backend.schedule.service.SchoolVacationCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherDashboardService {

    private final ScheduleRepository scheduleRepository;
    private final LessonRepository lessonRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TeacherDashboardResponse find(String teacherLogin) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(14);
        LocalDate end = today.minusDays(1);
        String academicYear = SchedulePeriod.academicYear(today);
        Map<String, Lesson> existing = new HashMap<>();
        for (Lesson lesson : lessonRepository.findAllForTeacherBetween(teacherLogin, start, end)) {
            existing.put(slotKey(lesson.getScheduleEntry().getId(), lesson.getDate()), lesson);
        }

        List<TeacherDashboardResponse.LessonWithoutTopic> pending = new ArrayList<>();
        for (ScheduleEntry entry : scheduleRepository.findJournalAssignmentsForTeacher(teacherLogin, academicYear)) {
            if (!entry.isActive()) continue;
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                if (entry.getEffectiveFrom() != null && date.isBefore(entry.getEffectiveFrom())) continue;
                if (SchoolVacationCalendar.isVacation(date)) continue;
                if (entry.getWeekNumber() != SchedulePeriod.weekNumber(date)) continue;
                if (!entry.getWeekday().name().equals(date.getDayOfWeek().name())) continue;
                Lesson lesson = existing.get(slotKey(entry.getId(), date));
                if (lesson != null && (lesson.getStatus() == LessonStatus.CANCELLED
                    || lesson.getTopic() != null && !lesson.getTopic().isBlank())) continue;
                pending.add(new TeacherDashboardResponse.LessonWithoutTopic(
                    entry.getSchoolClass().getId(), entry.getSubject().getId(),
                    entry.getSchoolClass().getDisplayName(), entry.getSubject().getTitle(),
                    date, entry.getLessonNumber()
                ));
            }
        }
        pending.sort(Comparator.comparing(TeacherDashboardResponse.LessonWithoutTopic::date).reversed()
            .thenComparing(TeacherDashboardResponse.LessonWithoutTopic::lessonNumber));

        List<TeacherDashboardResponse.RecentJournalChange> recent = lessonRepository
            .findRecentJournalChangesForTeacher(teacherLogin, LessonStatus.CANCELLED, PageRequest.of(0, 4))
            .stream().map(lesson -> {
                ScheduleEntry entry = lesson.getScheduleEntry();
                return new TeacherDashboardResponse.RecentJournalChange(
                    lesson.getId(),
                    entry.getSchoolClass().getId(), entry.getSubject().getId(),
                    entry.getSchoolClass().getDisplayName(), entry.getSubject().getTitle(),
                    lesson.getDate(), entry.getLessonNumber(), lesson.getTopic(), lesson.getUpdatedAt()
                );
            }).toList();
        return new TeacherDashboardResponse(pending.size(), pending.stream().limit(3).toList(), recent);
    }

    private String slotKey(Long scheduleId, LocalDate date) {
        return scheduleId + ":" + date;
    }
}
