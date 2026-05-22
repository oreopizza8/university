package com.diagnostic.service;

import com.diagnostic.dto.DiagnosticRequest;
import com.diagnostic.dto.DiagnosticResponse;
import com.diagnostic.dto.SolutionReport;
import com.diagnostic.entity.HighSchool;
import com.diagnostic.entity.SusiCutoff;
import com.diagnostic.repository.HighSchoolRepository;
import com.diagnostic.repository.SusiCutoffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 수시 학생부교과(내신) 진단. 내신 평균등급을 목표 학과의 70%컷 등급과 비교.
 * 등급은 작을수록 우수 → gap(=컷-내신)이 +면 합격선 위.
 */
@Service
@RequiredArgsConstructor
public class SusiDiagnosticService {

    private final SusiCutoffRepository susiRepo;
    private final HighSchoolRepository highSchoolRepo;
    private final HighSchoolContextService highSchoolContextService;

    private static final String SOURCE = "2025 어디가 공식 수시 내신 입결 기준";

    public DiagnosticResponse diagnose(DiagnosticRequest req) {
        Double naesin = req.getNaesinAverage();
        HighSchoolContextService.Context hs = resolveHs(req);

        if (naesin == null) {
            return base(req, hs).cutoffAvailable(false)
                    .summary("수시 진단에는 내신 평균등급 입력이 필요합니다.")
                    .dataSource(SOURCE)
                    .build();
        }

        // 고1·2(2025~ 신규 5등급제): [5등급 내신 → 전국 백분위 → 9등급 등가]로 치환해 9등급 기준 입결과 비교한다.
        boolean five = isFiveScale(req);
        double nat = five ? susi5GradeToPercentile(naesin) : gradeToPercentile(naesin);
        double naesin9 = five ? round2(percentileToSusi9Grade(nat)) : naesin;   // 컷(9등급) 비교용 환산값
        String naesinLabel = five
                ? String.format("%.2f등급(5등급제·9등급 환산 ≈ %.2f)", naesin, naesin9)
                : String.format("%.2f등급", naesin);

        List<SusiCutoff> all = susiRepo.findByUniversityNameAndDepartmentName(
                nz(req.getTargetUniversity()), nz(req.getTargetDepartment()));
        List<SusiCutoff> valid = new ArrayList<>();
        for (SusiCutoff s : all) if (s.getGrade70() != null && s.getGrade70() >= 1 && s.getGrade70() <= 9) valid.add(s);

        if (valid.isEmpty()) {
            return base(req, hs).cutoffAvailable(false)
                    .nationalPercentile(round(nat))
                    .summary("이 대학·학과의 수시 내신 70%컷 데이터가 아직 없어 합격선 판정은 보류했습니다. 내신 상위 위치만 참고하세요.")
                    .dataSource(SOURCE)
                    .solution(noDataSolution(naesinLabel, five))
                    .build();
        }

        // 대표 전형 = 70%컷 등급의 중앙값 전형
        valid.sort(Comparator.comparingDouble(SusiCutoff::getGrade70));
        SusiCutoff pick = valid.get(valid.size() / 2);
        double cut = pick.getGrade70();
        double gap = round2(cut - naesin9);         // 9등급 척도. +면 내가 컷보다 우수
        Signal sig = Signal.ofSusiGradeGap(gap);

        return base(req, hs)
                .targetUniversity(pick.getUniversityName())
                .targetDepartment(pick.getDepartmentName())
                .admissionType(pick.getAdmissionType())
                .targetCutoffGrade(cut)
                .nationalPercentile(round(nat))
                .signal(sig.label).probabilityMin(sig.min).probabilityMax(sig.max)
                .summary(String.format("내신 평균 %s / %s 70%%컷 %.2f등급 → 격차 %+.2f등급",
                        naesinLabel, nz2(pick.getAdmissionType()), cut, gap))
                .dataSource(SOURCE + " · 전형 다수 시 중앙값 기준" + (five ? " · 5등급제→9등급 치환" : ""))
                .solution(solution(naesinLabel, cut, gap, sig, pick, valid, five))
                .build();
    }

    private DiagnosticResponse.DiagnosticResponseBuilder base(DiagnosticRequest req, HighSchoolContextService.Context hs) {
        return DiagnosticResponse.builder()
                .mode("SUSI")
                .targetUniversity(nz(req.getTargetUniversity()))
                .targetDepartment(nz(req.getTargetDepartment()))
                .highSchoolType(hs.type()).highSchoolContext(hs.message())
                .guideQuestions(buildQuestions(hs));
    }

    private List<String> buildQuestions(HighSchoolContextService.Context hs) {
        List<String> q = new ArrayList<>(List.of(
                "학생부교과 반영 방식(전과목/주요과목/학년별 가중)이 정확히 어떻게 되나요?",
                "진로선택과목·전문교과 반영 방식과 가산점이 있나요?",
                "수능 최저학력기준이 있나요? 있다면 충족 가능성은요?",
                "이 내신대 학생의 작년 실제 합격/불합격 비율 데이터를 볼 수 있나요?"));
        q.addAll(hs.extraQuestions());
        return q;
    }

    private static final String SCALE_NOTE =
            "※ 고1·2 신규 5등급제 내신을 전국 백분위로 변환한 뒤, 과거 9등급 기준 입결 컷과 동일 척도로 맞춰 비교한 결과입니다(패러다임 치환). 대학 고유 반영방식에 따라 신호가 달라질 수 있습니다.";

    private SolutionReport solution(String naesinLabel, double cut, double gap, Signal sig,
                                    SusiCutoff pick, List<SusiCutoff> valid, boolean five) {
        String rationale = String.format(
                "내신 평균 %s은 %s %s 70%%컷 %.2f등급 대비 %+.2f등급입니다. 70%%컷은 합격자의 70%%가 이 등급 이상인 안정선이라, %s",
                naesinLabel, pick.getUniversityName(), nz2(pick.getAdmissionType()), cut, gap, meaning(sig));

        List<String> next = new ArrayList<>();
        if (gap < 0) next.add(String.format("목표컷까지 %s내신 %.2f등급 부족합니다. 남은 학기 주요과목 등급을 끌어올리면 격차가 줄어듭니다.",
                five ? "(9등급 환산 기준) " : "", -gap));
        else next.add("이미 70%컷 위입니다. 내신 외에 수능최저·면접 요건을 점검하세요.");
        if (valid.size() > 1) {
            double easy = valid.get(valid.size() - 1).getGrade70();
            double hard = valid.get(0).getGrade70();
            next.add(String.format("같은 학과 전형별 70%%컷이 %.2f~%.2f등급으로 분포합니다. 전형별 반영방식이 다르니 유리한 전형을 찾으세요.", hard, easy));
        }

        List<String> strategy = new ArrayList<>();
        strategy.add(gap >= 0.2 ? "내신 안정권입니다. 수시 교과를 안정 카드로 두고 상향 1~2장을 더 노려보세요."
                : gap >= -0.2 ? "경계선입니다. 교과 한 장에 의존하지 말고 종합·논술·정시와 분산하세요."
                : "교과로는 상향입니다. 학생부종합·논술 등 내신 외 요소가 강한 전형을 함께 검토하세요.");
        if (five) strategy.add(SCALE_NOTE);
        strategy.add("수능 최저학력기준 충족 여부가 실질 합격선을 좌우합니다. 최저부터 확인하세요.");
        return SolutionReport.builder().rationale(rationale).subjects(List.of()).nextSteps(next).strategy(strategy).build();
    }

    private SolutionReport noDataSolution(String naesinLabel, boolean five) {
        List<String> strategy = new ArrayList<>();
        if (five) strategy.add(SCALE_NOTE);
        strategy.add("교과 외 학생부종합·논술 전형의 가능성도 함께 검토하세요.");
        return SolutionReport.builder()
                .rationale(String.format("입력하신 대학·학과의 수시 내신 70%%컷 데이터가 없어 합격 판정은 보류했습니다. 내신 평균 %s의 전국 상위 위치만 참고하세요.", naesinLabel))
                .subjects(List.of())
                .nextSteps(List.of("목표 대학·학과의 최근 수시 교과 70%컷 등급(대입정보포털 '어디가')을 확인해 보세요."))
                .strategy(strategy)
                .build();
    }

    private HighSchoolContextService.Context resolveHs(DiagnosticRequest req) {
        Optional<HighSchool> hs = Optional.empty();
        if (req.getHighSchoolCode() != null && !req.getHighSchoolCode().isBlank())
            hs = highSchoolRepo.findBySchoolCode(req.getHighSchoolCode());
        if (hs.isEmpty() && req.getHighSchoolName() != null && !req.getHighSchoolName().isBlank())
            hs = highSchoolRepo.findTop20BySchoolNameContaining(req.getHighSchoolName()).stream().findFirst();
        return highSchoolContextService.of(hs.orElse(null));
    }

    private String meaning(Signal s) {
        return switch (s) {
            case SAFE -> "합격 안정권입니다.";
            case MODERATE -> "합격 가능하나 안심하긴 이른 적정권입니다.";
            case AGGRESSIVE -> "합격 가능성이 낮은 소신권입니다.";
            case RISKY -> "현재 내신으로는 위험권입니다.";
        };
    }

    /** 고1·2 = 2025~ 신규 5등급제 세대. (정시·수능 모의는 학년 무관 9등급 유지) */
    private boolean isFiveScale(DiagnosticRequest req) {
        return req.getStudentGrade() != null && req.getStudentGrade() <= 2;
    }

    /** 9등급제 내신 등급(1~9, 소수)을 전국 상위 백분위로 선형 보간. */
    private double gradeToPercentile(double g) {
        double[] p = {0, 96, 89, 77, 60, 40, 23, 11, 4, 1}; // index=등급
        g = Math.max(1, Math.min(9, g));
        int lo = (int) Math.floor(g), hi = (int) Math.ceil(g);
        if (lo == hi) return p[lo];
        double frac = g - lo;
        return p[lo] + (p[hi] - p[lo]) * frac;
    }

    /**
     * 신규 5등급제 내신(고1·2) → 전국 상위 백분위(선형 보간).
     * 각 등급대 누적분포의 중앙값 지점: 1등급=상위 5%, 2등급=22%, 3등급=50%, 4등급=78%, 5등급=95%.
     */
    private double susi5GradeToPercentile(double g) {
        double[] p = {0, 95, 78, 50, 22, 5}; // index=등급(1~5)
        g = Math.max(1, Math.min(5, g));
        int lo = (int) Math.floor(g), hi = (int) Math.ceil(g);
        if (lo == hi) return p[lo];
        return p[lo] + (p[hi] - p[lo]) * (g - lo);
    }

    /** 전국 상위 백분위 → 9등급제 등가 내신(gradeToPercentile의 역함수, 선형 보간). */
    private double percentileToSusi9Grade(double pct) {
        double[] p = {0, 96, 89, 77, 60, 40, 23, 11, 4, 1}; // 등급↑ → 백분위↓ (단조 감소)
        if (pct >= p[1]) return 1;
        if (pct <= p[9]) return 9;
        for (int g = 1; g < 9; g++) {
            if (pct <= p[g] && pct >= p[g + 1]) return g + (pct - p[g]) / (p[g + 1] - p[g]);
        }
        return 9;
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private String nz(String s) { return s == null ? "" : s; }
    private String nz2(String s) { return (s == null || s.isBlank()) ? "교과전형" : s; }
}
