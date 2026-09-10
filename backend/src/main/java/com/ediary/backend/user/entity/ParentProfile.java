package com.ediary.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent")
@Getter
@Setter
@NoArgsConstructor
public class ParentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parent_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "parent_name", nullable = false, length = 50)
    private String name;

    @Column(name = "parent_surname", nullable = false, length = 50)
    private String surname;

    @Column(name = "parent_patronymic", length = 50)
    private String patronymic;

    @Column(name = "parent_birthdate", nullable = false)
    private LocalDate birthDate;

    @Column(name = "parent_email", nullable = false, length = 100)
    private String email;

    @Column(name = "parent_phone", nullable = false, length = 20)
    private String phone;
}
