package com.diagnostic.dto;

import lombok.*;

import java.util.List;

/**
 * 상담 전 자가 솔루션 리포트. 진단→분석→처방→전략 순서로 학생 본인에게 주는 실질 가이드.
 * (압박질문이 "학원 검증용"이라면, 이쪽은 "내가 지금 뭘 해야 하나"에 답한다.)
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SolutionReport {

    /** 1. 판정 근거 — 왜 이 신호가 나왔는지 투명하게. */
    private String rationale;

    /** 2. 취약/강점 과목 진단. */
    private List<SubjectInsight> subjects;

    /** 3. 점수 민감도 처방 — "○○ 올리면 판정이 △△로". */
    private List<String> nextSteps;

    /** 4. 전형/지원 전략. */
    private List<String> strategy;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubjectInsight {
        private String subject;
        private Integer grade;       // 국내: 등급(1~9). 해외: null
        private Double percentile;   // 환산 백분위 또는 지표 위치
        private Double weight;       // 국내: 반영비율. 해외: null
        private String role;         // 강점 / 보통 / 취약
        private String note;         // 한 줄 코멘트
    }
}
