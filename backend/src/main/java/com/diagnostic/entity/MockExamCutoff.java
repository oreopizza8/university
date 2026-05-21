package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mock_exam_cutoffs", indexes = {
        @Index(name = "idx_exam_subject_grade", columnList = "examYear,examMonth,subjectName,grade")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockExamCutoff {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer examYear;
    private Integer examMonth;

    @Column(length = 50)
    private String subjectName;

    private Integer grade;
    private Integer standardScore;
    private Integer percentile;

    private Double subjectMean;
    private Double subjectSd;
}
