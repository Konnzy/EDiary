package com.ediary.backend.user.profile.dto;

public record UpdateNotificationPreferencesRequest(
    Boolean newGrade,
    Boolean newHomework,
    Boolean absence,
    Boolean newMessage,
    Boolean scheduleChange
) { }
