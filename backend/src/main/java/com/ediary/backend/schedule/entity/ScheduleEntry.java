package com.ediary.backend.schedule.entity;

import com.ediary.backend.user.entity.TeacherProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "schedule_weekday", nullable = false, columnDefinition = "enum_weekday")
    private Weekday weekday;

    @Column(name = "schedule_lesson_number", nullable = false)
    private Integer lessonNumber;

    @Column(name = "schedule_room", length = 20)
    private String room;

    @Column(name = "schedule_start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "schedule_end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "schedule_academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "schedule_week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "schedule_active", nullable = false)
    private boolean active = true;

    @Column(name = "schedule_archived_at")
    private LocalDateTime archivedAt;
}
