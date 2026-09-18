package com.ediary.backend.school.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.request.UpdateLessonRequest;
import com.ediary.backend.school.dto.response.LessonResponse;
import com.ediary.backend.school.entity.Lesson;
import com.ediary.backend.school.entity.LessonStatus;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.Weekday;
import com.ediary.backend.school.repository.LessonRepository;
import com.ediary.backend.school.repository.ScheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final LessonRepository lessonRepository;
    private final ScheduleRepository scheduleRepository;
    private final Clock clock;

    @Transactional
    public LessonResponse openForTeacher(String teacherLogin, Long scheduleId, LocalDate date) {
        ScheduleEntry schedule = scheduleRepository.findByIdForLessonCreation(scheduleId)
            .orElseThrow(this::scheduleNotFound);
        requireAssignedTeacher(schedule, teacherLogin);

        Lesson lesson = lessonRepository.findByScheduleEntry_IdAndDate(scheduleId, date)
            .orElse(null);
        if (lesson != null) {
            return toResponse(lesson);
        }

        if (!schedule.isActive()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SCHEDULE_ENTRY_ARCHIVED",
                "Неможливо створити урок із заархівованого запису розкладу"
            );
        }
        validateScheduledDate(schedule, date);

        LocalDateTime now = LocalDateTime.now(clock);
        lesson = new Lesson();
        lesson.setScheduleEntry(schedule);
        lesson.setDate(date);
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);
        return toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional(readOnly = true)
    public LessonResponse findForTeacher(String teacherLogin, Long lessonId) {
        return toResponse(requireTeacherLesson(teacherLogin, lessonId));
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> findAllForTeacherByDate(String teacherLogin, LocalDate date) {
        return lessonRepository.findAllForTeacherByDate(teacherLogin, date).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public LessonResponse updateForTeacher(
        String teacherLogin,
        Long lessonId,
        UpdateLessonRequest request
    ) {
        Lesson lesson = requireTeacherLesson(teacherLogin, lessonId);
        String topic = normalizeTopic(request.topic());
        validateStatusTransition(lesson.getStatus(), request.status());
        if (request.status() == LessonStatus.COMPLETED && topic == null) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "LESSON_TOPIC_REQUIRED",
                "Для завершеного уроку необхідно вказати тему"
            );
        }

        lesson.setTopic(topic);
        lesson.setStatus(request.status());
        lesson.setUpdatedAt(LocalDateTime.now(clock));
        return toResponse(lessonRepository.saveAndFlush(lesson));
    }

    private Lesson requireTeacherLesson(String teacherLogin, Long lessonId) {
        return lessonRepository.findForTeacher(lessonId, teacherLogin)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "LESSON_NOT_FOUND",
                "Урок не знайдено"
            ));
    }

    private void requireAssignedTeacher(ScheduleEntry schedule, String teacherLogin) {
        String assignedLogin = schedule.getTeacher().getUser().getLogin();
        if (!assignedLogin.equalsIgnoreCase(teacherLogin)) {
            throw new ApplicationException(
                HttpStatus.FORBIDDEN,
                "LESSON_ACCESS_DENIED",
                "Цей урок призначений іншому вчителю"
            );
        }
    }

    private void validateScheduledDate(ScheduleEntry schedule, LocalDate date) {
        if (date == null) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "LESSON_DATE_REQUIRED",
                "Вкажіть дату уроку"
            );
        }
        if (!schedule.getAcademicYear().equals(SchedulePeriod.academicYear(date))) {
            throw invalidDate();
        }
        if (schedule.getWeekNumber() != SchedulePeriod.weekNumber(date)) {
            throw invalidDate();
        }
        if (schedule.getWeekday() != Weekday.valueOf(date.getDayOfWeek().name())) {
            throw invalidDate();
        }
    }

    private void validateStatusTransition(LessonStatus current, LessonStatus requested) {
        if (current == requested) {
            return;
        }
        boolean allowed = switch (current) {
            case PLANNED -> requested == LessonStatus.IN_PROGRESS
                || requested == LessonStatus.COMPLETED
                || requested == LessonStatus.CANCELLED;
            case IN_PROGRESS -> requested == LessonStatus.COMPLETED
                || requested == LessonStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "INVALID_LESSON_STATUS_TRANSITION",
                "Неможливо змінити статус уроку з " + current + " на " + requested
            );
        }
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        return topic.strip().replaceAll("\\s+", " ");
    }

    private LessonResponse toResponse(Lesson lesson) {
        ScheduleEntry schedule = lesson.getScheduleEntry();
        var teacher = schedule.getTeacher();
        return new LessonResponse(
            lesson.getId(),
            schedule.getId(),
            lesson.getDate(),
            lesson.getStatus(),
            lesson.getTopic(),
            schedule.getSchoolClass().getId(),
            schedule.getSchoolClass().getDisplayName(),
            schedule.getSubject().getId(),
            schedule.getSubject().getTitle(),
            teacher.getId(),
            teacher.getSurname() + " " + teacher.getName()
                + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                    ? ""
                    : " " + teacher.getPatronymic()),
            schedule.getLessonNumber(),
            schedule.getRoom(),
            schedule.getStartTime().format(TIME_FORMAT),
            schedule.getEndTime().format(TIME_FORMAT),
            lesson.getCreatedAt(),
            lesson.getUpdatedAt()
        );
    }

    private ApplicationException scheduleNotFound() {
        return new ApplicationException(
            HttpStatus.NOT_FOUND,
            "SCHEDULE_ENTRY_NOT_FOUND",
            "Запис розкладу не знайдено"
        );
    }

    private ApplicationException invalidDate() {
        return new ApplicationException(
            HttpStatus.BAD_REQUEST,
            "LESSON_DATE_DOES_NOT_MATCH_SCHEDULE",
            "Дата не відповідає навчальному року, тижню або дню цього запису розкладу"
        );
    }
}
