package com.diagnostic.service;

import com.diagnostic.dto.MyLineRequest;
import com.diagnostic.dto.MyLineResponse;
import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 내 라인 찾기(Bottom-Up) — 핵심 주기능 2.
 * 내 국/수/탐 백분위 평균을 기준으로 합격 컷이 [score-safe, score+reach] 범위인
 * 전국 대학/학과를 매칭해 신호(안정/적정/소신/위험) 라벨과 함께 반환한다.
 *
 * <p>gap = 내백분위 - 컷. 안정선(gap +safe)~소신선(gap -reach) 사이를 본다.
 * 산식은 정시 진단과 동일한 70%컷·Signal 을 재사용한다(별도 모델 없음).
 */
@Service
@RequiredArgsConstructor
public class MyLineService {

    /** 신호(안정/적정/소신/위험)별 최대 노출 수. 균형 할당으로 안정~위험 스펙트럼이 모두 보이게 한다. */
    private static final int PER_SIGNAL = 40;
    /** 결과 표시 순서: 안정 → 적정 → 소신 → 위험. */
    private static final List<String> SIGNAL_ORDER = List.of(
            Signal.SAFE.label, Signal.MODERATE.label, Signal.AGGRESSIVE.label, Signal.RISKY.label);

    private final DomesticUniversityCutoffRepository domesticRepo;

    public MyLineResponse find(MyLineRequest req) {
        double score = req.getAvgPercentile();
        double low = round1(score - req.safeMarginOrDefault());   // 안정선(컷이 내 점수보다 낮음)
        double high = round1(score + req.reachMarginOrDefault()); // 소신선(컷이 내 점수보다 높음)

        List<MyLineResponse.Item> all = domesticRepo
                .findByCutoffPercentile70BetweenOrderByCutoffPercentile70Desc(low, high).stream()
                .filter(c -> c.getCutoffPercentile70() != null)
                .map(c -> toItem(score, c))
                .toList();
        int totalInRange = all.size();

        // 신호별 그룹핑 후, 각 신호 안에서 내 점수에 가까운 순(|gap|)으로 PER_SIGNAL개까지.
        // 단순 컷 내림차순+전체캡은 넓은 범위에서 가장 어려운 학교(위험)만 남기고 안정권을 잘라내므로 균형 할당한다.
        Map<String, List<MyLineResponse.Item>> bySignal = all.stream()
                .collect(Collectors.groupingBy(MyLineResponse.Item::getSignal, LinkedHashMap::new, Collectors.toList()));
        List<MyLineResponse.Item> items = new ArrayList<>();
        for (String sig : SIGNAL_ORDER) {
            List<MyLineResponse.Item> bucket = bySignal.get(sig);
            if (bucket == null) continue;
            bucket.stream()
                    .sorted(Comparator.comparingDouble(it -> Math.abs(it.getGap())))
                    .limit(PER_SIGNAL)
                    .forEach(items::add);
        }

        return MyLineResponse.builder()
                .avgPercentile(score)
                .rangeLow(low)
                .rangeHigh(high)
                .totalCount(totalInRange)
                .items(items)
                .dataSource("2025 어디가 공식 정시 입결(70%컷 백분위) · 반영비율 계열 추정")
                .note("내 백분위 평균을 기준으로 한 0단계 위치 스크리닝입니다. 합격 확률 정밀예측이 아니며, "
                        + "실제 지원은 대학별 반영비율·군 조합·표준점수를 반영한 학원 컨설팅으로 검증하세요.")
                .build();
    }

    private MyLineResponse.Item toItem(double score, DomesticUniversityCutoff c) {
        double cut = c.getCutoffPercentile70();
        double gap = round1(score - cut);
        Signal s = Signal.ofDomesticGap(gap);
        return MyLineResponse.Item.builder()
                .universityName(c.getUniversityName())
                .departmentName(c.getDepartmentName())
                .cutoffPercentile70(cut)
                .gap(gap)
                .signal(s.label)
                .probMin(s.min)
                .probMax(s.max)
                .recruitCount(c.getRecruitCount())
                .competitionRate(c.getCompetitionRate())
                .additionalRank(c.getAdditionalRank())
                .koreanCut70(c.getKoreanCut70())
                .mathCut70(c.getMathCut70())
                .tamguCut70(c.getTamguCut70())
                .region(c.getRegion())
                .admissionGroup(c.getAdmissionGroup())
                .build();
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
