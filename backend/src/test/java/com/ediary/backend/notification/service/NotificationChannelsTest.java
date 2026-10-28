package com.ediary.backend.notification.service;

import com.ediary.backend.notification.entity.UserNotificationSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationChannelsTest {
    private static final String[] CATEGORIES = {"GRADE", "HOMEWORK", "HOMEWORK_DEADLINE", "ABSENCE", "MESSAGE",
        "SCHEDULE", "CLASS_SCHEDULE", "PROLONGED_ABSENCE", "CLASS_ROSTER"};

    @Test
    void allKnownCategoriesAreInAppAndEmailIsOptIn() {
        var settings = new UserNotificationSettings();
        for (String category : CATEGORIES) {
            settings.getInApp().put(NotificationChannels.key(category), false);
            assertThat(NotificationChannels.inApp(null, category)).isTrue();
            assertThat(NotificationChannels.inApp(settings, category)).isTrue();
            assertThat(NotificationChannels.email(null, category)).isFalse();
            assertThat(NotificationChannels.email(settings, category)).isFalse();
        }
        settings.setAttendanceEmailEnabled(true);
        assertThat(NotificationChannels.email(settings, "ABSENCE")).isTrue();
        assertThat(NotificationChannels.email(settings, "GRADE")).isFalse();
        assertThat(NotificationChannels.inApp(settings, "UNKNOWN")).isFalse();
        assertThat(NotificationChannels.email(settings, "UNKNOWN")).isFalse();
    }
}
