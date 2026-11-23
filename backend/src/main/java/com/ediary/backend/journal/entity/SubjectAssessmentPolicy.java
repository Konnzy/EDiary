package com.ediary.backend.journal.entity;

import com.ediary.backend.journal.service.AssessmentPolicyService.Mode;
import com.ediary.backend.schedule.entity.Subject;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subject_assessment_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubjectAssessmentPolicy {
    @Id
    @Column(name = "subject_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private Mode mode;

    @Column(name = "allow_passed", nullable = false)
    private boolean allowPassed;

    @Column(name = "allow_exempted", nullable = false)
    private boolean allowExempted;

    public SubjectAssessmentPolicy(Subject subject) {
        this.subject = subject;
    }

    public void update(Mode mode, boolean allowPassed, boolean allowExempted) {
        this.mode = mode;
        this.allowPassed = allowPassed;
        this.allowExempted = allowExempted;
    }
}
