package com.ediary.backend.notification.service;

import com.ediary.backend.notification.entity.UserNotificationSettings;

public final class NotificationChannels {
    private NotificationChannels() {}

    public static String key(String category) {
        return switch (category) {
            case "GRADE" -> "newGrade";
            case "HOMEWORK" -> "newHomework";
            case "HOMEWORK_DEADLINE" -> "homeworkDeadlineReminder";
            case "ABSENCE" -> "absence";
            case "MESSAGE" -> "newMessage";
            case "SCHEDULE" -> "scheduleChange";
            case "CLASS_SCHEDULE" -> "classScheduleChange";
            case "PROLONGED_ABSENCE" -> "prolongedAbsence";
            case "CLASS_ROSTER" -> "classRosterChange";
            default -> "";
        };
    }

    public static boolean inApp(UserNotificationSettings settings, String category) {
        // In-app delivery is mandatory; legacy channel preferences no longer apply.
        return !key(category).isEmpty();
    }

    public static boolean email(UserNotificationSettings settings, String category) {
        if (key(category).isEmpty()) return false;
        if (settings == null) return false;
        return switch (category) {
            case "GRADE" -> settings.isGradeEmailEnabled();
            case "HOMEWORK" -> settings.isHomeworkEmailEnabled();
            case "HOMEWORK_DEADLINE" -> settings.isHomeworkDeadlineReminderEmailEnabled();
            case "ABSENCE" -> settings.isAttendanceEmailEnabled();
            case "MESSAGE" -> settings.isMessageEmailEnabled();
            case "SCHEDULE" -> settings.isScheduleChangeEmailEnabled();
            case "CLASS_SCHEDULE" -> settings.isClassScheduleChangeEmailEnabled();
            case "PROLONGED_ABSENCE" -> settings.isProlongedAbsenceEmailEnabled();
            case "CLASS_ROSTER" -> settings.isClassRosterChangeEmailEnabled();
            default -> false;
        };
    }
}
