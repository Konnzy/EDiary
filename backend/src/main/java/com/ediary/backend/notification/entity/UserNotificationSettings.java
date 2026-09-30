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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settings_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "grade_email_enabled", nullable = false)
    private boolean gradeEmailEnabled = true;

    @Column(name = "homework_email_enabled", nullable = false)
    private boolean homeworkEmailEnabled = true;

    @Column(name = "homework_deadline_reminder_email_enabled", nullable = false)
    private boolean homeworkDeadlineReminderEmailEnabled = true;

    @Column(name = "attendance_email_enabled", nullable = false)
    private boolean attendanceEmailEnabled = true;

    @Column(name = "homework_status_email_enabled", nullable = false)
    private boolean homeworkStatusEmailEnabled = true;

    @Column(name = "message_email_enabled", nullable = false)
    private boolean messageEmailEnabled = true;

    @Column(name = "schedule_change_email_enabled", nullable = false)
    private boolean scheduleChangeEmailEnabled = true;

    @Column(name = "class_schedule_change_email_enabled", nullable = false)
    private boolean classScheduleChangeEmailEnabled = true;

    @Column(name = "prolonged_absence_email_enabled", nullable = false)
    private boolean prolongedAbsenceEmailEnabled = true;

    @Column(name = "class_roster_change_email_enabled", nullable = false)
    private boolean classRosterChangeEmailEnabled = true;
}
