package com.ediary.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "student_parents")
@Getter
@NoArgsConstructor
public class StudentParentLink {

    @EmbeddedId
    private StudentParentId id;

    @MapsId("studentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @MapsId("parentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentProfile parent;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean primaryContact;

    public StudentParentLink(StudentProfile student, ParentProfile parent, boolean primaryContact) {
        this.student = student;
        this.parent = parent;
        this.primaryContact = primaryContact;
        this.id = new StudentParentId(student.getId(), parent.getId());
    }
}
