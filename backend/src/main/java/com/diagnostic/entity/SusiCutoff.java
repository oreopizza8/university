package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

/** 수시(학생부교과/종합) 내신 등급 컷. 어디가 2025 전형결과 PDF에서 추출. */
@Entity
@Table(name = "susi_cutoffs", indexes = {
        @Index(name = "idx_susi_uni_dept", columnList = "universityName,departmentName")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SusiCutoff {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String universityName;

    @Column(length = 100)
    private String admissionType;   // 전형명 (학생부교과 일반전형 등)

    @Column(length = 100)
    private String departmentName;

    private Double grade50;          // 내신 50%컷 등급
    private Double grade70;          // 내신 70%컷 등급 (1=최상 ~ 9)
}
