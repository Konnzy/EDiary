package com.ediary.backend.registration.entity;

import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.UserRole;
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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registration_request")
@Getter
@NoArgsConstructor
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "request_name", nullable = false, length = 50)
    private String name;

    @Column(name = "request_surname", nullable = false, length = 50)
    private String surname;

    @Column(name = "request_patronymic", length = 50)
    private String patronymic;

    @Column(name = "request_email", nullable = false, length = 100)
    private String email;

    @Column(name = "request_phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "request_birthdate", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "requested_role", nullable = false, columnDefinition = "enum_user_role")
    private UserRole requestedRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "request_status", nullable = false, columnDefinition = "enum_registration_status")
    private RegistrationStatus status;

    @Column(name = "request_terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    @Column(name = "request_created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_reviewed_by_user_id")
    private AppUser reviewedBy;

    @Column(name = "request_rejection_reason", length = 500)
    private String rejectionReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_processed_user_id", unique = true)
    private AppUser processedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_class_id")
    private SchoolClass requestedClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_subject_id")
    private Subject requestedSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_student_id")
    private StudentProfile requestedStudent;

    @Version
    @Column(name = "request_version", nullable = false)
    private long version;

    public RegistrationRequest(
        String name,
        String surname,
        String patronymic,
        String email,
        String phone,
        LocalDate birthDate,
        UserRole requestedRole,
        SchoolClass requestedClass,
        Subject requestedSubject,
        StudentProfile requestedStudent,
        LocalDateTime submittedAt
    ) {
        this.name = name;
        this.surname = surname;
        this.patronymic = patronymic;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.requestedRole = requestedRole;
        this.requestedClass = requestedClass;
        this.requestedSubject = requestedSubject;
        this.requestedStudent = requestedStudent;
        this.status = RegistrationStatus.PENDING;
        this.termsAcceptedAt = submittedAt;
        this.createdAt = submittedAt;
    }

    public void approve(AppUser reviewer, AppUser createdUser, LocalDateTime reviewedAt) {
        requirePending();
        this.status = RegistrationStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.processedUser = createdUser;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = null;
    }

    public void reject(AppUser reviewer, String reason, LocalDateTime reviewedAt) {
        requirePending();
        this.status = RegistrationStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = reason;
        this.processedUser = null;
    }

    private void requirePending() {
        if (status != RegistrationStatus.PENDING) {
            throw new IllegalStateException("Registration request has already been reviewed");
        }
    }
}
