package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "domestic_university_cutoffs", indexes = {
        @Index(name = "idx_uni_dept", columnList = "universityName,departmentName")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DomesticUniversityCutoff {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String universityName;

    @Column(length = 100)
    private String departmentName;

    private Double ratioKo;
    private Double ratioMath;
    private Double ratioEn;
    private Double ratioTg;

    private Double cutoffPercentile70;

    // 어디가 공식 실측 부가지표 (풍부 스키마 — 내 라인 찾기 결과 노출용). 시드/구 CSV엔 없어 null 가능.
    private Integer recruitCount;      // 모집인원(최종)
    private Double competitionRate;    // 경쟁률
    private Integer additionalRank;    // 충원합격순위(추합)
    private Double koreanCut70;        // 국어 70%컷 (Format B만)
    private Double mathCut70;          // 수학 70%컷
    private Double tamguCut70;         // 탐구 70%컷

    @Column(length = 20)
    private String region;             // 시도 (지역 필터용)
    @Column(length = 10)
    private String admissionGroup;     // 모집군 가/나/다 (군 필터용)

    @Column(length = 200)
    private String dataSource;          // 출처·단위 라벨 (행별 — 어디가/입학처 등 구분). null이면 서비스 기본값.
}
