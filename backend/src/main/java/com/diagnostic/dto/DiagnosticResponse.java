package com.diagnostic.dto;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DiagnosticResponse {

    private String mode;
    private Double nationalPercentile;

    /** 목표 대학·학과의 입결(70%컷/CDS) 데이터 보유 여부. false면 합격 판정을 보류하고 위치·조언만 제공. */
    @Builder.Default
    private Boolean cutoffAvailable = true;

    private String signal;
    private Integer probabilityMin;
    private Integer probabilityMax;

    private String targetUniversity;
    private String targetDepartment;
    private Double targetCutoffPercentile;   // 정시 70%컷 백분위
    private Double targetCutoffGrade;        // 수시 70%컷 내신 등급
    private String admissionType;            // 수시 전형명

    private Double convertedGpa;
    private Integer convertedSat;

    private String summary;
    private List<String> guideQuestions;

    /** 출신 고교 유형(일반고/특목고/자사고 등) 기반 수시 환경 안내. 정시 백분위에는 영향 없음. */
    private String highSchoolType;
    private String highSchoolContext;

    /** 상담 전 자가 솔루션 리포트 (판정 근거·과목 진단·민감도·전략). */
    private SolutionReport solution;

    /** 데이터 출처·신뢰도 표기 (예: "2025 어디가 공식 입결 · 반영비율은 계열 추정"). */
    private String dataSource;
}
