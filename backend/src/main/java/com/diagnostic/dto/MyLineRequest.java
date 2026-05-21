package com.diagnostic.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 내 라인 찾기(Bottom-Up) 요청.
 * 사용자가 본인 국/수/탐 백분위 평균을 던지면, 그 점수 주변대(소신선~안정선)의
 * 합격 컷을 가진 전국 대학/학과를 반환한다.
 */
@Data
public class MyLineRequest {

    /** 내 국/수/탐 백분위 평균(0~100). 범위검색의 기준점. */
    @NotNull(message = "평균 백분위는 필수입니다.")
    @DecimalMin(value = "0.0", message = "백분위는 0~100 사이여야 합니다.")
    @DecimalMax(value = "100.0", message = "백분위는 0~100 사이여야 합니다.")
    private Double avgPercentile;

    /** 안정선 마진(컷이 내 점수보다 이만큼 낮은 곳까지). 기본 5.0 → 하한 = score - 5. */
    @DecimalMin(value = "0.0") @DecimalMax(value = "30.0")
    private Double safeMargin;

    /** 소신선 마진(컷이 내 점수보다 이만큼 높은 곳까지). 기본 2.0 → 상한 = score + 2. */
    @DecimalMin(value = "0.0") @DecimalMax(value = "30.0")
    private Double reachMargin;

    public double safeMarginOrDefault() { return safeMargin != null ? safeMargin : 5.0; }
    public double reachMarginOrDefault() { return reachMargin != null ? reachMargin : 2.0; }
}
