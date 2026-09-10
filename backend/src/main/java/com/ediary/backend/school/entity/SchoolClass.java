package com.ediary.backend.school.entity;

import com.ediary.backend.user.entity.TeacherProfile;
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
@Table(name = "school_class")
@Getter
@Setter
@NoArgsConstructor
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_teacher_id", unique = true)
    private TeacherProfile classTeacher;

    @Column(name = "class_number", nullable = false)
    private Integer number;

    @Column(name = "class_letter", nullable = false, length = 5)
    private String letter;

    @Column(name = "class_limit", nullable = false)
    private Integer limit;

    public String getDisplayName() {
        return number + "-" + letter;
    }
}
