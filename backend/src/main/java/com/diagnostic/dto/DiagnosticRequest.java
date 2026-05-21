package com.diagnostic.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DiagnosticRequest {

    @NotNull(message = "isGlobal 플래그는 필수입니다.")
    private Boolean isGlobal;

    @Size(max = 30, message = "시험 종류는 30자 이내여야 합니다.")
    private String examType;

    @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
    @Max(value = 3, message = "학년은 3 이하여야 합니다.")
    private Integer studentGrade;

    @NotNull(message = "국어 등급은 필수입니다.")
    @Min(value = 1, message = "등급은 1~9 사이여야 합니다.")
    @Max(value = 9, message = "등급은 1~9 사이여야 합니다.")
    private Integer koreanGrade;

    @NotNull(message = "수학 등급은 필수입니다.")
    @Min(value = 1, message = "등급은 1~9 사이여야 합니다.")
    @Max(value = 9, message = "등급은 1~9 사이여야 합니다.")
    private Integer mathGrade;

    @NotNull(message = "영어 등급은 필수입니다.")
    @Min(value = 1, message = "등급은 1~9 사이여야 합니다.")
    @Max(value = 9, message = "등급은 1~9 사이여야 합니다.")
    private Integer englishGrade;

    @Min(value = 1, message = "등급은 1~9 사이여야 합니다.")
    @Max(value = 9, message = "등급은 1~9 사이여야 합니다.")
    private Integer tg1Grade;

    @Min(value = 1, message = "등급은 1~9 사이여야 합니다.")
    @Max(value = 9, message = "등급은 1~9 사이여야 합니다.")
    private Integer tg2Grade;

    @Min(value = 0, message = "백분위 점수는 0~100 사이여야 합니다.")
    @Max(value = 100, message = "백분위 점수는 0~100 사이여야 합니다.")
    private Integer koreanPercentile;

    @Min(value = 0, message = "백분위 점수는 0~100 사이여야 합니다.")
    @Max(value = 100, message = "백분위 점수는 0~100 사이여야 합니다.")
    private Integer mathPercentile;

    @Min(value = 0, message = "백분위 점수는 0~100 사이여야 합니다.")
    @Max(value = 100, message = "백분위 점수는 0~100 사이여야 합니다.")
    private Integer englishPercentile;

    @Size(max = 100, message = "목표 대학명은 100자 이내여야 합니다.")
    private String targetUniversity;

    @Size(max = 100, message = "목표 학과명은 100자 이내여야 합니다.")
    private String targetDepartment;

    @Size(max = 20, message = "고교 코드는 20자 이내여야 합니다.")
    private String highSchoolCode;

    @Size(max = 100, message = "고교명은 100자 이내여야 합니다.")
    private String highSchoolName;

    /** 국내 진단 유형: JEONGSI(정시·기본) / SUSI(수시 내신). */
    @Size(max = 10, message = "진단 유형이 올바르지 않습니다.")
    private String diagnosisType;

    /** 수시 모드용 내신 평균등급 (1.0~9.0). */
    @DecimalMin(value = "1.0", message = "내신 등급은 1.0~9.0 사이여야 합니다.")
    @DecimalMax(value = "9.0", message = "내신 등급은 1.0~9.0 사이여야 합니다.")
    private Double naesinAverage;
}
