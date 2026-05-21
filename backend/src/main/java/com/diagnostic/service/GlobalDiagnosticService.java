package com.diagnostic.service;

import com.diagnostic.dto.DiagnosticRequest;
import com.diagnostic.dto.DiagnosticResponse;
import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.repository.GlobalUniversityCutoffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GlobalDiagnosticService {

    private final ScoreConversionService conv;
    private final GlobalUniversityCutoffRepository globalRepo;
    private final SolutionService solutionService;

    public DiagnosticResponse diagnose(DiagnosticRequest req) {
        int mainGrade = averageMainGrade(req);
        double gpa = conv.koreanGradeToGpa(mainGrade);

        double pKo = conv.gradeToTopPercentile(req.getKoreanGrade());
        double pMath = conv.gradeToTopPercentile(req.getMathGrade());
        int sat = conv.percentileSumToSat(pKo, pMath);

        Optional<GlobalUniversityCutoff> cutoffOpt =
                globalRepo.findByUniversityName(req.getTargetUniversity());

        // CDS(SAT/GPA) 데이터가 없으면 가짜 CDS로 판정하지 않고 정직하게 보류 (국내 M8 정책과 일치).
        if (cutoffOpt.isEmpty()) {
            String inputName = (req.getTargetUniversity() == null || req.getTargetUniversity().isBlank())
                    ? "(미입력 대학)" : req.getTargetUniversity();
            return DiagnosticResponse.builder()
                    .mode("GLOBAL")
                    .cutoffAvailable(false)
                    .convertedGpa(gpa)
                    .convertedSat(sat)
                    .targetUniversity(inputName)
                    .targetDepartment(req.getTargetDepartment())
                    .summary(String.format("이 대학의 CDS(SAT/GPA 25-75) 데이터가 아직 없어 합격선 판정은 보류했습니다. "
                            + "환산 GPA %.2f / 가상 SAT %d 는 등급 기반 추정치이니 참고만 하세요.", gpa, sat))
                    .guideQuestions(globalGuideQuestions())
                    .dataSource("CDS 데이터 미보유 · 환산 GPA/SAT는 등급 기반 추정")
                    .build();
        }

        GlobalUniversityCutoff cutoff = cutoffOpt.get();

        int satRange75 = cutoff.getSatMath75th() + cutoff.getSatReading75th();
        int satRange25 = cutoff.getSatMath25th() + cutoff.getSatReading25th();

        double satScorePos = positionRatio(sat, satRange25, satRange75);
        double gpaScorePos = positionRatio(gpa * 100, cutoff.getAvgGpa() * 100 - 20, cutoff.getAvgGpa() * 100 + 20);

        double composite = (satScorePos + gpaScorePos) / 2.0;
        Signal bucket = Signal.ofGlobalComposite(composite);

        return DiagnosticResponse.builder()
                .mode("GLOBAL")
                .cutoffAvailable(true)
                // 게이지 = 한국 전국 백분위(무의미)가 아니라 "목표교 CDS 대비 종합 위치" 0~100
                .nationalPercentile(round(composite * 100.0))
                .signal(bucket.label)
                .probabilityMin(bucket.min)
                .probabilityMax(bucket.max)
                .targetUniversity(cutoff.getUniversityName())
                .targetDepartment(req.getTargetDepartment())
                .convertedGpa(gpa)
                .convertedSat(sat)
                .summary(String.format("환산 GPA %.2f / 가상 SAT %d. 목표교 CDS 25-75 분포 SAT %d~%d, 평균 GPA %.2f 와 비교",
                        gpa, sat, satRange25, satRange75, cutoff.getAvgGpa()))
                .guideQuestions(globalGuideQuestions())
                .solution(solutionService.buildGlobal(req, cutoff, sat, gpa, satScorePos, gpaScorePos, composite, bucket))
                .build();
    }

    private List<String> globalGuideQuestions() {
        return List.of(
                "이 대학의 최근 3년 합격자 CDS(SAT/GPA 25-75) 자료 보여주실 수 있나요?",
                "한국 내신을 GPA로 환산하는 우리 유학원만의 공식이 있나요? 공식 산식 공개 부탁드립니다.",
                "에세이/추천서 평가 비중과 정량 점수의 비중을 어떻게 나누나요?",
                "이 학교에 보낸 작년 실제 합격 학생 수와 평균 스펙은 어떻게 되나요?"
        );
    }

    /** 국·수·영 주요 3과목 평균 등급(반올림). GPA 환산의 기준 등급. */
    private int averageMainGrade(DiagnosticRequest req) {
        double avg = ((double) req.getKoreanGrade() + req.getMathGrade() + req.getEnglishGrade()) / 3.0;
        return (int) Math.round(avg);
    }

    private double positionRatio(double value, double low, double high) {
        if (high <= low) return 0.5;
        double r = (value - low) / (high - low);
        return Math.max(0.0, Math.min(1.0, r));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
