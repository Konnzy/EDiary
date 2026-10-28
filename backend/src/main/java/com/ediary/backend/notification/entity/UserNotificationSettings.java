package com.ediary.backend.notification.entity;

import com.ediary.backend.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyColumn;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationSettings {

    // Retained for historical data only; in-app delivery no longer reads these preferences.
    @ElementCollection
    @CollectionTable(name = "notification_in_app_preference", joinColumns = @JoinColumn(name = "settings_id"))
    @MapKeyColumn(name = "preference_key", length = 60)
    @Column(name = "enabled", nullable = false)
    private Map<String, Boolean> inApp = new HashMap<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settings_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "grade_email_enabled", nullable = false)
    private boolean gradeEmailEnabled = false;

    @Column(name = "homework_email_enabled", nullable = false)
    private boolean homeworkEmailEnabled = false;

    @Column(name = "homework_deadline_reminder_email_enabled", nullable = false)
    private boolean homeworkDeadlineReminderEmailEnabled = false;

    @Column(name = "attendance_email_enabled", nullable = false)
    private boolean attendanceEmailEnabled = false;

    @Column(name = "homework_status_email_enabled", nullable = false)
    private boolean homeworkStatusEmailEnabled = false;

    @Column(name = "message_email_enabled", nullable = false)
    private boolean messageEmailEnabled = false;

    @Column(name = "schedule_change_email_enabled", nullable = false)
    private boolean scheduleChangeEmailEnabled = false;

    @Column(name = "class_schedule_change_email_enabled", nullable = false)
    private boolean classScheduleChangeEmailEnabled = false;

    @Column(name = "prolonged_absence_email_enabled", nullable = false)
    private boolean prolongedAbsenceEmailEnabled = false;

    @Column(name = "class_roster_change_email_enabled", nullable = false)
    private boolean classRosterChangeEmailEnabled = false;
}
