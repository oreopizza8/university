package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "global_university_cutoffs", indexes = {
        @Index(name = "idx_country_uni", columnList = "country,universityName")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GlobalUniversityCutoff {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String country;

    @Column(length = 100)
    private String universityName;

    private Integer satMath25th;
    private Integer satMath75th;
    private Integer satReading25th;
    private Integer satReading75th;

    private Double avgGpa;
}
