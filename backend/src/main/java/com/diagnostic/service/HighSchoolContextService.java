package com.diagnostic.service;

import com.diagnostic.entity.HighSchool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 출신 고교 유형(NEIS HS_SC_NM + 설립구분)에 따른 "수시 내신환경" 안내 생성기.
 *
 * <p>Why 정시 백분위를 보정하지 않는가:
 * 본 진단의 입력은 모의고사/수능 등급으로, 전국 단위 상대평가다. 대형학원(메가스터디·진학사 등)도
 * 정시 합격예측에서는 고교 유형 보정을 하지 않는다(고교와 무관하게 동일 의미). 학원이 고교 유형 보정을
 * 적용하는 영역은 <b>수시 학생부(내신)</b>다. 따라서 고교 유형은 정시 백분위를 건드리지 않고,
 * "같은 정시 성적이라도 출신고에 따라 수시 전략이 달라진다"는 학원식 안내·체크포인트로만 반영한다.
 */
@Service
public class HighSchoolContextService {

    public record Context(String type, String message, List<String> extraQuestions) {}

    /** 고교 정보가 없거나 매칭 실패 시 빈 컨텍스트. */
    public Context of(HighSchool hs) {
        if (hs == null) return new Context(null, null, List.of());

        String raw = hs.getHighSchoolType() == null ? "" : hs.getHighSchoolType();
        boolean isPrivate = hs.getFoundationType() != null && hs.getFoundationType().contains("사립");

        // 특목고(과학고·영재고·외고·국제고)
        if (raw.contains("특수목적") || raw.contains("과학") || raw.contains("외국어") || raw.contains("국제")) {
            return new Context("특목고",
                    "특목고는 내신 산출이 불리한 대신 학생부종합·논술·정시에서 강세를 보이는 것이 일반적입니다. "
                    + "수시 학생부교과(내신 100%) 전형은 불리할 수 있으니, 정시 위치를 주력으로 두고 학종 비교과·세특 경쟁력을 함께 점검하세요.",
                    List.of(
                            "우리 학교 출신의 최근 학종 합격 사례에서 내신 등급대가 어디까지 내려갔나요?",
                            "이 대학 학종이 교과 세특·전공적합성을 정량 내신보다 얼마나 비중 있게 보나요?"));
        }

        // 자사고(자율형사립고) — 자율고 + 사립
        if ((raw.contains("자율") && isPrivate)) {
            return new Context("자사고",
                    "자사고는 내신 경쟁이 치열해 학생부교과 전형에서 불리할 수 있으나, 정시·논술에서 강세가 흔합니다. "
                    + "내신 대비 정시 위치가 좋다면 정시·논술 카드를 우선 검토하세요.",
                    List.of("우리 학교의 정시 합격 실적이 수시 실적 대비 어떤가요?"));
        }

        // 자공고/자율형공립고 등 기타 자율고
        if (raw.contains("자율")) {
            return new Context("자율고",
                    "자율고는 학교별 편차가 큽니다. 내신 산출 유불리와 정시 실적을 학교 자료로 직접 확인해 수시·정시 비중을 정하세요.",
                    List.of("우리 학교 내신 평균이 인근 일반고 대비 어느 수준인가요?"));
        }

        // 특성화고
        if (raw.contains("특성화") || raw.contains("마이스터")) {
            return new Context("특성화고",
                    "특성화고는 특성화고 전형·동일계 특별전형 등 별도 트랙이 핵심입니다. 일반 정시·수시와 별개로 특별전형 요건을 우선 확인하세요.",
                    List.of("특성화고 특별전형/동일계 전형으로 지원 가능한 학과와 정원이 어떻게 되나요?"));
        }

        // 일반고(기본)
        if (raw.contains("일반")) {
            return new Context("일반고",
                    "일반고는 수시 학생부교과(내신) 전형 활용 폭이 넓습니다. 모의고사 위치 대비 내신이 더 좋다면 수시 교과 카드를, 반대면 정시를 주력으로 검토하세요.",
                    List.of("내 내신 등급이 이 대학 학생부교과 전형의 최근 70%컷과 비교해 어디쯤인가요?"));
        }

        return new Context(raw.isBlank() ? null : raw, null, List.of());
    }
}
