package com.ediary.backend.school.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "lesson",
    uniqueConstraints = @UniqueConstraint(
        name = "unique_lesson_per_schedule_date",
        columnNames = {"schedule_id", "lesson_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ScheduleEntry scheduleEntry;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate date;

    @Column(name = "lesson_topic", length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "lesson_status", nullable = false, columnDefinition = "enum_lesson_status")
    private LessonStatus status;

    @Column(name = "lesson_created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "lesson_updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
