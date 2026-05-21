package com.diagnostic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 내 라인 찾기(Bottom-Up) 응답: 내 백분위 주변대 합격선 학과 리스트 + 신호별 그룹.
 */
@Data @Builder @AllArgsConstructor
public class MyLineResponse {

    private double avgPercentile;   // 입력 기준점
    private double rangeLow;        // 검색 하한(안정선)
    private double rangeHigh;       // 검색 상한(소신선)
    private int totalCount;

    private List<Item> items;       // 컷 내림차순 전체
    private String dataSource;      // 출처 라벨
    private String note;            // 신뢰 범위 안내

    @Data @Builder @AllArgsConstructor
    public static class Item {
        private String universityName;
        private String departmentName;
        private double cutoffPercentile70;
        private double gap;          // 내 백분위 - 컷 (양수=내가 우위)
        private String signal;       // 안정/적정/소신/위험
        private int probMin;         // 합격 가능성 밴드(%) — 참고용
        private int probMax;
        // 어디가 공식 실측 부가지표 (없으면 null)
        private Integer recruitCount;     // 모집인원
        private Double competitionRate;   // 경쟁률
        private Integer additionalRank;   // 충원합격순위(추합)
        private Double koreanCut70;       // 국어 70%컷
        private Double mathCut70;         // 수학 70%컷
        private Double tamguCut70;        // 탐구 70%컷
        private String region;            // 시도 (지역 필터)
        private String admissionGroup;    // 모집군 (군 필터)
    }
}
