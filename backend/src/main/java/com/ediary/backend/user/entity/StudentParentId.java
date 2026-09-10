package com.ediary.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class StudentParentId implements Serializable {

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "parent_id")
    private Long parentId;

    public StudentParentId(Long studentId, Long parentId) {
        this.studentId = studentId;
        this.parentId = parentId;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof StudentParentId other)) return false;
        return Objects.equals(studentId, other.studentId) && Objects.equals(parentId, other.parentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, parentId);
    }
}
