package com.ediary.backend.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "academic_year")
@Getter
@Setter
@NoArgsConstructor
public class AcademicYear {

    @Id
    @Column(name = "academic_year_value", length = 9)
    private String value;
}
