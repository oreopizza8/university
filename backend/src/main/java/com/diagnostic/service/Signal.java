package com.diagnostic.service;

/**
 * 지원 가능성 신호 — 대형학원(메가스터디·진학사 등) 정시 지원가능성 관례에 맞춘 4단계.
 *
 * <p>합격선(목표 70%컷 / 목표교 CDS) 대비 내 위치로 구간을 나눈다.
 * 70%컷은 "합격자의 70%가 그 이상"인 안정선이므로, 컷 미만은 결코 '안정'이 될 수 없다.
 * (min/max 는 대략적 합격 가능성 밴드 — 실제 칸 보정은 누적 합불 데이터 확보 후 재캘리브레이션 대상.)
 */
public enum Signal {
    SAFE("안정", 75, 95),
    MODERATE("적정", 55, 75),
    AGGRESSIVE("소신", 30, 55),
    RISKY("위험", 5, 30);

    public final String label;
    public final int min;
    public final int max;

    Signal(String label, int min, int max) {
        this.label = label;
        this.min = min;
        this.max = max;
    }

    /**
     * 국내 정시: 가중 백분위와 목표 70%컷의 격차(pp) 기준.
     * gap >= 0  → 컷 이상 = 안정
     * gap >= -4 → 컷 근처(≈50%컷대) = 적정
     * gap >= -10→ 소신
     * 그 미만   → 위험
     */
    public static Signal ofDomesticGap(double gapPp) {
        if (gapPp >= 0.0) return SAFE;
        if (gapPp >= -4.0) return MODERATE;
        if (gapPp >= -10.0) return AGGRESSIVE;
        return RISKY;
    }

    /**
     * 해외: 목표교 CDS(SAT/GPA 25-75) 대비 종합 위치(0.0~1.0) 기준.
     * 0.5 = CDS 중앙값 부근. 0.75th(상위권) 이상이면 안정.
     */
    public static Signal ofGlobalComposite(double composite) {
        if (composite >= 0.60) return SAFE;
        if (composite >= 0.40) return MODERATE;
        if (composite >= 0.20) return AGGRESSIVE;
        return RISKY;
    }

    /**
     * 수시 내신: gap = (목표 70%컷 등급 - 내 내신 등급). 등급은 작을수록 우수하므로 gap이 +면 내가 컷보다 우수.
     */
    public static Signal ofSusiGradeGap(double gap) {
        if (gap >= 0.2) return SAFE;
        if (gap >= -0.2) return MODERATE;
        if (gap >= -0.7) return AGGRESSIVE;
        return RISKY;
    }
}
