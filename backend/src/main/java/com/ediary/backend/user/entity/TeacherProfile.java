package com.ediary.backend.user.entity;

import com.ediary.backend.schedule.entity.Subject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teacher")
@Getter
@Setter
@NoArgsConstructor
public class TeacherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "teacher_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "teacher_name", nullable = false, length = 50)
    private String name;

    @Column(name = "teacher_surname", nullable = false, length = 50)
    private String surname;

    @Column(name = "teacher_patronymic", length = 50)
    private String patronymic;

    @Column(name = "teacher_birthdate", nullable = false)
    private LocalDate birthDate;

    @Column(name = "teacher_email", nullable = false, length = 100)
    private String email;

    @Column(name = "teacher_phone", nullable = false, length = 20)
    private String phone;

    @ManyToMany
    @JoinTable(
        name = "teacher_subject",
        joinColumns = @JoinColumn(name = "teacher_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    private Set<Subject> subjects = new LinkedHashSet<>();
}
