package com.diagnostic.service;

import com.diagnostic.dto.DiagnosticRequest;
import com.diagnostic.dto.SolutionReport;
import com.diagnostic.dto.SolutionReport.SubjectInsight;
import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 상담 전 자가 솔루션 엔진. 진단 결과를 받아 "근거·과목진단·민감도·전략"을 생성한다.
 * 모든 산식은 진단에서 쓴 동일한 반영비율/컷/CDS를 그대로 재사용한다(따로 모델을 만들지 않음).
 */
@Service
@RequiredArgsConstructor
public class SolutionService {

    private final ScoreConversionService conv;
    private final DomesticUniversityCutoffRepository domesticRepo;

    // ===================== 국내 정시 =====================

    public SolutionReport buildDomestic(DiagnosticRequest req, DomesticUniversityCutoff cutoff,
                                        double pKo, double pMath, double pEn, double pTg,
                                        double weighted, double gap, Signal signal) {
        double cut70 = cutoff.getCutoffPercentile70();
        String target = cutoff.getUniversityName() + " " + cutoff.getDepartmentName();

        // 1. 근거
        String rationale = String.format(
                "가중 백분위 %.1f은 %s 70%%컷 %.1f 대비 %+.1fpp입니다. 70%%컷은 합격자의 70%%가 이 점수 이상인 안정선이라, %s",
                weighted, target, cut70, gap, signalMeaning(signal, gap));

        // 2. 과목 진단 (반영비율 가중 관점)
        Integer tgGrade = avgTgGrade(req);
        List<SubjectInsight> subjects = new ArrayList<>();
        subjects.add(subjectInsight("국어", req.getKoreanGrade(), pKo, cutoff.getRatioKo(), weighted));
        subjects.add(subjectInsight("수학", req.getMathGrade(), pMath, cutoff.getRatioMath(), weighted));
        subjects.add(subjectInsight("영어", req.getEnglishGrade(), pEn, cutoff.getRatioEn(), weighted));
        subjects.add(subjectInsight("탐구", tgGrade, pTg, cutoff.getRatioTg(), weighted));

        // 3. 민감도 처방
        List<String> nextSteps = new ArrayList<>();
        if (gap < 0) nextSteps.add(String.format("목표컷까지 가중 백분위 %.1fpp 부족합니다. 가장 효율적인 보완 과목은 아래와 같습니다.", -gap));
        else nextSteps.add("이미 목표컷 위입니다. 아래는 한 단계 더 끌어올렸을 때의 변화입니다.");

        addLever(nextSteps, "국어", req.getKoreanGrade(), pKo, cutoff.getRatioKo(), weighted, cut70, signal);
        addLever(nextSteps, "수학", req.getMathGrade(), pMath, cutoff.getRatioMath(), weighted, cut70, signal);
        addLever(nextSteps, "영어", req.getEnglishGrade(), pEn, cutoff.getRatioEn(), weighted, cut70, signal);
        if (tgGrade != null) addLever(nextSteps, "탐구", tgGrade, pTg, cutoff.getRatioTg(), weighted, cut70, signal);

        // 4. 전략
        List<String> strategy = new ArrayList<>();
        strategy.add(trackAdvice(req, gap));
        strategy.addAll(portfolio(cutoff, weighted));

        return SolutionReport.builder()
                .rationale(rationale)
                .subjects(subjects)
                .nextSteps(nextSteps)
                .strategy(strategy)
                .build();
    }

    private void addLever(List<String> out, String name, Integer grade, double pct, Double weight,
                          double weighted, double cut70, Signal current) {
        if (weight == null || weight <= 0 || grade == null || grade <= 1) return;
        double newPct = conv.gradeToTopPercentile(grade - 1);
        double newWeighted = weighted - pct * weight + newPct * weight;
        double newGap = newWeighted - cut70;
        Signal ns = Signal.ofDomesticGap(newGap);
        String tier = ns != current
                ? String.format("판정 %s→%s ✅", current.label, ns.label)
                : "판정 " + current.label + " 유지";
        out.add(String.format("· %s %d→%d등급: 가중 백분위 %.1f (%+.1fpp), %s",
                name, grade, grade - 1, newWeighted, newWeighted - weighted, tier));
    }

    // ===================== 국내: 입결 데이터 없음 =====================

    private static final double[] DEFAULT_RATIO = {0.25, 0.30, 0.20, 0.25}; // 국/수/영/탐 (반영비율 미상 시)

    public SolutionReport buildDomesticNoData(DiagnosticRequest req,
                                              double pKo, double pMath, double pEn, double pTg, double weighted) {
        String uni = isBlank(req.getTargetUniversity()) ? "입력하신 대학" : req.getTargetUniversity();
        String dept = isBlank(req.getTargetDepartment()) ? "" : " " + req.getTargetDepartment();

        String rationale = String.format(
                "%s%s의 정시 70%%컷 입결 데이터가 아직 없어 합격 가능성은 판정하지 못했습니다. "
                + "대신 전국 백분위(약 %.1f, 상위 %.1f%%)와 과목 강약점으로 방향을 제시합니다.",
                uni, dept, weighted, Math.max(0, 100 - weighted));

        List<SubjectInsight> subjects = new ArrayList<>();
        subjects.add(subjectInsight("국어", req.getKoreanGrade(), pKo, null, weighted));
        subjects.add(subjectInsight("수학", req.getMathGrade(), pMath, null, weighted));
        subjects.add(subjectInsight("영어", req.getEnglishGrade(), pEn, null, weighted));
        subjects.add(subjectInsight("탐구", avgTgGrade(req), pTg, null, weighted));

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("합격선 비교는 불가능한 상태라, 아래는 한 등급 향상 시 전국 백분위 변화입니다.");
        addNoDataLever(nextSteps, "국어", req.getKoreanGrade(), pKo, DEFAULT_RATIO[0], weighted);
        addNoDataLever(nextSteps, "수학", req.getMathGrade(), pMath, DEFAULT_RATIO[1], weighted);
        addNoDataLever(nextSteps, "영어", req.getEnglishGrade(), pEn, DEFAULT_RATIO[2], weighted);
        addNoDataLever(nextSteps, "탐구", avgTgGrade(req), pTg, DEFAULT_RATIO[3], weighted);

        List<String> strategy = new ArrayList<>();
        strategy.add("정확한 합격 판정을 위해, 목표 대학·학과의 최근 정시 70%컷(대입정보포털 '어디가' 또는 대학 발표)을 확인하세요.");
        strategy.add("아래는 내 전국 백분위 기준으로 입결이 등록된 학과 중 비슷한 라인입니다(참고용).");
        List<String> safe = pickLines(domesticRepo.findTop8ByCutoffPercentile70LessThanEqualOrderByCutoffPercentile70Desc(weighted), null, weighted);
        List<String> reach = pickLines(domesticRepo.findTop8ByCutoffPercentile70GreaterThanOrderByCutoffPercentile70Asc(weighted), null, weighted);
        if (!safe.isEmpty()) strategy.add("내 점수 이하 컷: " + String.join(", ", safe.subList(0, Math.min(3, safe.size()))));
        if (!reach.isEmpty()) strategy.add("내 점수 초과 컷: " + String.join(", ", reach.subList(0, Math.min(3, reach.size()))));

        return SolutionReport.builder()
                .rationale(rationale).subjects(subjects).nextSteps(nextSteps).strategy(strategy).build();
    }

    private void addNoDataLever(List<String> out, String name, Integer grade, double pct, double w, double weighted) {
        if (grade == null || grade <= 1) return;
        double newPct = conv.gradeToTopPercentile(grade - 1);
        double newWeighted = weighted + (newPct - pct) * w; // 기본 반영비율 가정 하의 전국 백분위 근사 변화
        out.add(String.format("· %s %d→%d등급: 전국 백분위 %.1f (%+.1fpp)", name, grade, grade - 1, newWeighted, newWeighted - weighted));
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ===================== 국내: 지원 포트폴리오 =====================

    private List<String> portfolio(DomesticUniversityCutoff target, double weighted) {
        List<String> lines = new ArrayList<>();
        List<String> safe = pickLines(domesticRepo.findTop8ByCutoffPercentile70LessThanEqualOrderByCutoffPercentile70Desc(weighted), target, weighted);
        List<String> reach = pickLines(domesticRepo.findTop8ByCutoffPercentile70GreaterThanOrderByCutoffPercentile70Asc(weighted), target, weighted);
        if (!safe.isEmpty()) lines.add("안정 지원 후보(내 점수가 컷 이상): " + String.join(", ", safe.subList(0, Math.min(3, safe.size()))));
        if (!reach.isEmpty()) lines.add("소신 지원 후보(컷이 내 점수보다 높음): " + String.join(", ", reach.subList(0, Math.min(3, reach.size()))));
        if (lines.isEmpty()) lines.add("같은 점수대 분산 지원을 위해 안정·적정·소신 3개 라인으로 카드를 나누세요. (현재 입결 DB 표본이 적어 구체 추천은 데이터 보강 후 제공됩니다.)");
        return lines;
    }

    private List<String> pickLines(List<DomesticUniversityCutoff> list, DomesticUniversityCutoff target, double weighted) {
        List<String> out = new ArrayList<>();
        for (DomesticUniversityCutoff c : list) {
            if (c.getUniversityName() == null || c.getUniversityName().startsWith("(")) continue;
            if (target != null && c.getUniversityName().equals(target.getUniversityName())
                    && c.getDepartmentName() != null && c.getDepartmentName().equals(target.getDepartmentName())) continue;
            if (c.getCutoffPercentile70() == null) continue;
            out.add(String.format("%s %s(컷 %.0f)", c.getUniversityName(), c.getDepartmentName(), c.getCutoffPercentile70()));
        }
        return out;
    }

    private String trackAdvice(DiagnosticRequest req, double gap) {
        String hs = req.getHighSchoolName();
        StringBuilder sb = new StringBuilder();
        if (gap >= 0) sb.append("정시 기준 합격선 위입니다. 같은 점수대에서 상향 1~2장을 더 노려볼 여지가 있습니다.");
        else if (gap >= -4) sb.append("정시로는 적정~경계권. 정시 한 장에 올인하기보다 수시 카드를 병행해 안전망을 두세요.");
        else sb.append("정시만으로는 무리한 라인입니다. 수시(학생부·논술)와 점수 향상 둘 다 필요합니다.");
        return sb.toString();
    }

    private SubjectInsight subjectInsight(String name, Integer grade, double pct, Double weight, double weighted) {
        String role;
        if (pct >= weighted + 4) role = "강점";
        else if (pct <= weighted - 4) role = "취약";
        else role = "보통";
        double w = weight == null ? 0 : weight;
        String note;
        if ("취약".equals(role) && w >= 0.25) note = String.format("반영 %.0f%% 핵심 과목인데 약점 — 최우선 보완 대상", w * 100);
        else if ("취약".equals(role)) note = "평균 대비 발목을 잡는 과목";
        else if ("강점".equals(role) && w >= 0.25) note = String.format("반영 %.0f%% 비중 과목에서 강점 — 합격 견인", w * 100);
        else if ("강점".equals(role)) note = "평균을 끌어올리는 과목";
        else note = "평균 수준";
        return SubjectInsight.builder()
                .subject(name).grade(grade).percentile(round(pct)).weight(weight).role(role).note(note).build();
    }

    private String signalMeaning(Signal s, double gap) {
        return switch (s) {
            case SAFE -> "합격 안정권입니다.";
            case MODERATE -> "합격은 가능하나 안심하긴 이른 적정권입니다.";
            case AGGRESSIVE -> "합격 가능성이 낮은 소신권입니다.";
            case RISKY -> "현재 점수로는 위험권입니다.";
        };
    }

    private Integer avgTgGrade(DiagnosticRequest req) {
        Integer t1 = req.getTg1Grade();
        Integer t2 = req.getTg2Grade();
        if (t1 == null && t2 == null) return null;
        if (t1 == null) return t2;
        if (t2 == null) return t1;
        return (int) Math.round((t1 + t2) / 2.0);
    }

    // ===================== 해외 유학 =====================

    public SolutionReport buildGlobal(DiagnosticRequest req, GlobalUniversityCutoff cutoff,
                                      int sat, double gpa, double satPos, double gpaPos,
                                      double composite, Signal signal) {
        int satRange25 = cutoff.getSatMath25th() + cutoff.getSatReading25th();
        int satRange75 = cutoff.getSatMath75th() + cutoff.getSatReading75th();
        String target = cutoff.getUniversityName();

        // 1. 근거
        String satWhere = positionWord(satPos);
        String gpaWhere = gpa >= cutoff.getAvgGpa() ? "이상" : "미만";
        String rationale = String.format(
                "환산 SAT %d은 %s CDS(%d~%d)의 %s, 환산 GPA %.2f는 평균 %.2f %s입니다. 두 지표 위치를 합쳐 %s",
                sat, target, satRange25, satRange75, satWhere, gpa, cutoff.getAvgGpa(), gpaWhere, signalMeaning(signal, 0));

        // 2. 지표 진단
        List<SubjectInsight> subjects = new ArrayList<>();
        subjects.add(SubjectInsight.builder().subject("SAT").percentile(round(satPos * 100))
                .role(satPos >= 0.5 ? "강점" : (satPos <= 0.25 ? "취약" : "보통"))
                .note(String.format("CDS 25-75(%d~%d) 대비 위치", satRange25, satRange75)).build());
        subjects.add(SubjectInsight.builder().subject("GPA").percentile(round(gpaPos * 100))
                .role(gpaPos >= 0.5 ? "강점" : (gpaPos <= 0.25 ? "취약" : "보통"))
                .note(String.format("평균 GPA %.2f 대비 위치", cutoff.getAvgGpa())).build());

        // 3. 민감도 처방
        List<String> nextSteps = new ArrayList<>();
        if (satPos <= gpaPos) {
            int targetSat = (int) Math.round((satRange25 + satRange75) / 2.0 / 10.0) * 10;
            nextSteps.add(String.format("두 지표 중 SAT가 상대적 약점입니다. CDS 중앙값(약 %d)까지 올리면 합격선 진입에 가장 직접적입니다.", targetSat));
        } else {
            nextSteps.add(String.format("두 지표 중 GPA가 상대적 약점입니다. 내신 관리로 평균 %.2f에 근접시키는 것이 우선입니다.", cutoff.getAvgGpa()));
        }
        nextSteps.add("정량 점수 외에 이 학교가 중시하는 에세이·추천서·비교과로 격차를 일부 메울 수 있는지 점검하세요.");

        // 4. 전략
        List<String> strategy = new ArrayList<>();
        strategy.add(reachAdvice(signal, target));
        strategy.add("지원 포트폴리오는 reach(목표교) / match(CDS 중앙값이 내 점수와 겹치는 학교) / safety(25%컷이 내 점수 이하인 학교) 3단계로 분산하세요.");
        strategy.add("미국 상위권은 정량 점수만으로 합불이 갈리지 않습니다. 활동·전공적합성 스토리를 함께 준비하세요.");

        return SolutionReport.builder()
                .rationale(rationale).subjects(subjects).nextSteps(nextSteps).strategy(strategy).build();
    }

    private String reachAdvice(Signal s, String target) {
        return switch (s) {
            case SAFE -> target + "은 정량상 안정권이나, 미국 입시 특성상 안심 금물 — match/safety도 반드시 함께 지원하세요.";
            case MODERATE -> target + "은 match(적정) 라인입니다. safety 1~2곳을 반드시 추가하세요.";
            case AGGRESSIVE -> target + "은 소신(reach) 라인입니다. match·safety 비중을 더 두세요.";
            case RISKY -> target + "은 현재 정량으로 깊은 reach입니다. 비교과·에세이 강점이 없다면 match/safety 중심으로 재설계하세요.";
        };
    }

    private String positionWord(double pos) {
        if (pos <= 0.0) return "하단(25%컷 이하)";
        if (pos < 0.5) return "하위 구간";
        if (pos < 0.75) return "중상위 구간";
        return "상단(75%컷 이상)";
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
