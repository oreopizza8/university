package com.diagnostic.service;

import com.diagnostic.dto.DiagnosticRequest;
import com.diagnostic.dto.DiagnosticResponse;
import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.entity.HighSchool;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import com.diagnostic.repository.HighSchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DomesticDiagnosticService {

    private final ScoreConversionService conv;
    private final DomesticUniversityCutoffRepository domesticRepo;
    private final HighSchoolRepository highSchoolRepo;
    private final HighSchoolContextService highSchoolContextService;
    private final SolutionService solutionService;

    public DiagnosticResponse diagnose(DiagnosticRequest req) {
        double pKo = conv.gradeToTopPercentile(req.getKoreanGrade());
        double pMath = conv.gradeToTopPercentile(req.getMathGrade());
        double pEn = conv.gradeToTopPercentile(req.getEnglishGrade());
        double pTg = averageTg(req);

        Optional<DomesticUniversityCutoff> cutoffOpt =
                domesticRepo.findByUniversityNameAndDepartmentName(req.getTargetUniversity(), req.getTargetDepartment());

        // 출신 고교 유형 기반 수시 환경 안내 (정시 백분위에는 영향 없음 — 모의고사는 전국 상대평가)
        HighSchoolContextService.Context hsContext = resolveHighSchoolContext(req);

        List<String> guideQuestions = new ArrayList<>(List.of(
                "최근 3개년 정시 70% 컷이 우리 학교 평균보다 어떻게 움직였나요?",
                "수시 내신 반영 방식(전과목/주요과목)이 정확히 어떻게 되나요?",
                "표준점수·백분위 중 어떤 지표를 환산식에 쓰나요? 가중치 산식 공개 부탁드립니다.",
                "이 점수대 학생이 작년에 실제로 합격/불합격한 비율 데이터 보여주실 수 있나요?"
        ));
        guideQuestions.addAll(hsContext.extraQuestions());

        // 입결(70%컷) 데이터가 없으면 가짜 컷으로 판정하지 않고, 위치+조언만 정직하게 제공.
        if (cutoffOpt.isEmpty() || cutoffOpt.get().getCutoffPercentile70() == null) {
            double weightedDefault = pKo * 0.25 + pMath * 0.30 + pEn * 0.20 + pTg * 0.25;
            return DiagnosticResponse.builder()
                    .mode("DOMESTIC")
                    .cutoffAvailable(false)
                    .nationalPercentile(round(weightedDefault))
                    .targetUniversity(blankToInput(req.getTargetUniversity(), "(미입력 대학)"))
                    .targetDepartment(blankToInput(req.getTargetDepartment(), ""))
                    .summary("이 대학·학과의 정시 70%컷 입결 데이터가 아직 없어 합격선 판정은 보류했습니다. 아래 전국 백분위와 일반 조언을 참고하세요.")
                    .guideQuestions(guideQuestions)
                    .highSchoolType(hsContext.type())
                    .highSchoolContext(hsContext.message())
                    .solution(solutionService.buildDomesticNoData(req, pKo, pMath, pEn, pTg, weightedDefault))
                    .dataSource("입결 데이터 미보유 · 전국 백분위는 표준 반영비율 기준 근사")
                    .build();
        }

        DomesticUniversityCutoff cutoff = cutoffOpt.get();

        double weighted = pKo * cutoff.getRatioKo()
                + pMath * cutoff.getRatioMath()
                + pEn * cutoff.getRatioEn()
                + pTg * cutoff.getRatioTg();

        double signalGap = weighted - cutoff.getCutoffPercentile70();
        Signal bucket = Signal.ofDomesticGap(signalGap);

        return DiagnosticResponse.builder()
                .mode("DOMESTIC")
                .nationalPercentile(round(weighted))
                .signal(bucket.label)
                .probabilityMin(bucket.min)
                .probabilityMax(bucket.max)
                .targetUniversity(cutoff.getUniversityName())
                .targetDepartment(cutoff.getDepartmentName())
                .targetCutoffPercentile(cutoff.getCutoffPercentile70())
                .summary(String.format("반영비율 가중 백분위 %.1f / 목표컷 70%% 컷 %.1f → 격차 %.1fpp",
                        weighted, cutoff.getCutoffPercentile70(), signalGap))
                .guideQuestions(guideQuestions)
                .highSchoolType(hsContext.type())
                .highSchoolContext(hsContext.message())
                .solution(solutionService.buildDomestic(req, cutoff, pKo, pMath, pEn, pTg, weighted, signalGap, bucket))
                .dataSource(cutoff.getDataSource() != null && !cutoff.getDataSource().isBlank()
                        ? cutoff.getDataSource()
                        : "2025 어디가 공식 정시 입결(70%컷 백분위) · 반영비율은 계열 추정")
                .build();
    }

    private HighSchoolContextService.Context resolveHighSchoolContext(DiagnosticRequest req) {
        Optional<HighSchool> hs = Optional.empty();
        if (req.getHighSchoolCode() != null && !req.getHighSchoolCode().isBlank()) {
            hs = highSchoolRepo.findBySchoolCode(req.getHighSchoolCode());
        }
        if (hs.isEmpty() && req.getHighSchoolName() != null && !req.getHighSchoolName().isBlank()) {
            hs = highSchoolRepo.findTop20BySchoolNameContaining(req.getHighSchoolName())
                    .stream().findFirst();
        }
        return highSchoolContextService.of(hs.orElse(null));
    }

    private double averageTg(DiagnosticRequest req) {
        Integer t1 = req.getTg1Grade();
        Integer t2 = req.getTg2Grade();
        if (t1 == null && t2 == null) return 0.0;
        if (t1 == null) return conv.gradeToTopPercentile(t2);
        if (t2 == null) return conv.gradeToTopPercentile(t1);
        return (conv.gradeToTopPercentile(t1) + conv.gradeToTopPercentile(t2)) / 2.0;
    }

    private String blankToInput(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
