package com.ediary.backend.user.profile.dto;

import com.ediary.backend.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileDetailsResponse(
    Long id,
    UserRole role,
    String firstName,
    String lastName,
    String patronymic,
    String login,
    String email,
    String phone,
    LocalDateTime registeredAt,
    Long avatarVersion,
    Education education,
    Professional professional,
    List<Child> children,
    NotificationPreferences notifications
) {
    public record Education(
        String className,
        String teacherName,
        String teacherEmail,
        String teacherPhone,
        String parentEmail,
        String parentPhone
    ) { }

    public record Professional(
        String position,
        List<String> subjects,
        List<String> classes,
        List<String> homeroomClasses
    ) { }

    public record Child(
        Long id,
        String fullName,
        String email,
        String className,
        String teacherName,
        String teacherEmail,
        String teacherPhone
    ) { }

    public record NotificationPreferences(
        boolean newGrade,
        boolean newHomework,
        boolean absence,
        boolean newMessage,
        boolean scheduleChange,
        boolean classScheduleChange,
        boolean prolongedAbsence,
        boolean classRosterChange
    ) { }
}
