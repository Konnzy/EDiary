package com.ediary.backend.user.entity;

import com.ediary.backend.schedule.entity.SchoolClass;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @Column(name = "student_name", nullable = false, length = 50)
    private String name;

    @Column(name = "student_surname", nullable = false, length = 50)
    private String surname;

    @Column(name = "student_patronymic", length = 50)
    private String patronymic;

    @Column(name = "student_birthdate", nullable = false)
    private LocalDate birthDate;

    @Column(name = "student_enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "student_status", nullable = false, columnDefinition = "enum_student_status")
    private StudentStatus status;

    @Column(name = "parent_link_code_hash", length = 64, unique = true)
    private String parentLinkCodeHash;

    @Column(name = "parent_link_code_created_at")
    private LocalDateTime parentLinkCodeCreatedAt;
}
