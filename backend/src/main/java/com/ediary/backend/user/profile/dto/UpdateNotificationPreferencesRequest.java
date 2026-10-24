package com.ediary.backend.user.profile.dto;

import java.util.Map;

public record UpdateNotificationPreferencesRequest(
    Boolean newGrade,
    Boolean newHomework,
    Boolean absence,
    Boolean newMessage,
    Boolean scheduleChange,
    Boolean classScheduleChange,
    Boolean prolongedAbsence,
    Boolean classRosterChange,
    Boolean homeworkDeadlineReminder,
    Map<String, Boolean> inApp
) {
    public UpdateNotificationPreferencesRequest(Boolean newGrade, Boolean newHomework, Boolean absence,
        Boolean newMessage, Boolean scheduleChange, Boolean classScheduleChange, Boolean prolongedAbsence,
        Boolean classRosterChange, Boolean homeworkDeadlineReminder) {
        this(newGrade, newHomework, absence, newMessage, scheduleChange, classScheduleChange,
            prolongedAbsence, classRosterChange, homeworkDeadlineReminder, null);
    }
}
