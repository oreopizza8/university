package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "university_programs", indexes = {
        @Index(name = "idx_prog_uni", columnList = "universityName"),
        @Index(name = "idx_prog_dept", columnList = "departmentName"),
        @Index(name = "idx_prog_code", columnList = "universityCode,departmentCode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UniversityProgram {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100) private String universityName;
    @Column(length = 20)  private String universityCode;
    @Column(length = 30)  private String universityType;
    @Column(length = 20)  private String region;
    @Column(length = 100) private String collegeName;
    @Column(length = 20)  private String departmentCode;
    @Column(length = 150) private String departmentName;
    @Column(length = 50)  private String fieldL1;
    @Column(length = 50)  private String fieldL2;
    @Column(length = 50)  private String fieldL3;
    @Column(length = 30)  private String degreeType;
}
