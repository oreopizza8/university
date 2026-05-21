# 백엔드 청사진 — 정시 입결 마이그레이션 + 두 핵심 기능 쿼리

> 대상 데이터: `integrated_jeongsi_results_2025.csv` (대교협 '어디가' 2025 정시 전형결과, 풍부 스키마)
> 두 핵심 주기능: ① 목표 대학 매칭(Top-Down, 단건 조회) ② 내 라인 찾기(Bottom-Up, 범위 BETWEEN 검색)
> 신뢰 원칙: 컷·모집인원·경쟁률·추합은 **공식 실측값**, 반영비율(ratio_*)만 **계열 표준 추정치**(모집요강 부재). 이 경계를 결과에 항상 명시한다.

---

## 1. CSV 스키마 (정제 산출물)

| CSV 컬럼 | 의미 | 출처 | 비고 |
|---|---|---|---|
| region | 시도 | 파일명 | 인덱스 보조 |
| universityName | 대학명 | 표지 큰글씨 | 조회 키 |
| admissionGroup | 모집군(가/나/다) | 표 '구분' | 정시 군별 |
| departmentName | 모집단위(학과) | 표 '모집단위' | 조회 키 |
| recruitCount | 모집인원(최종 A+B) | 표 실측 | |
| competitionRate | 경쟁률 | 표 실측 | `6.8:1`→6.8 정규화 |
| additionalRank | 충원합격순위(추합) | 표 실측 | `-`→공백 |
| koreanCut70 | 국어 70%컷 백분위 | 표 실측 | Format B만, 없으면 공백 |
| mathCut70 | 수학 70%컷 백분위 | 표 실측 | Format B만 |
| tamguCut70 | 탐구 70%컷 백분위(탐1·탐2 평균) | 표 실측 | Format B만 |
| **percentile70** | **수능평균 70%컷 백분위** | 표 실측 | **★범위검색 메인 인덱스** (Format A: 단일평균, Format B: 과목평균) |
| ratioKor/ratioMath/ratioEng/ratioTam | 국/수/영/탐 반영비율 | **계열 추정** | 실제값 확보 시 대체 |

---

## 2. MySQL 테이블 생성문 (DDL)

```sql
CREATE TABLE jeongsi_cutoffs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    region           VARCHAR(20),
    university_name  VARCHAR(100) NOT NULL,
    admission_group  VARCHAR(10),                  -- 가/나/다군
    department_name  VARCHAR(100) NOT NULL,
    recruit_count    INT,                          -- 모집인원(최종)
    competition_rate DECIMAL(6,2),                 -- 경쟁률
    additional_rank  INT,                          -- 충원합격순위(추합)
    korean_cut70     DECIMAL(5,2),                 -- 국어 70%컷 (NULL 가능)
    math_cut70       DECIMAL(5,2),                 -- 수학 70%컷 (NULL 가능)
    tamgu_cut70      DECIMAL(5,2),                 -- 탐구 70%컷 (NULL 가능)
    percentile70     DECIMAL(5,2) NOT NULL,        -- ★수능평균 70%컷 (범위검색 메인 인덱스)
    ratio_kor        DECIMAL(4,3),                 -- 계열 추정 반영비율
    ratio_math       DECIMAL(4,3),
    ratio_eng        DECIMAL(4,3),
    ratio_tam        DECIMAL(4,3),
    PRIMARY KEY (id),
    -- ① 목표 대학 매칭(Top-Down): 대학+학과 단건 조회용
    KEY idx_uni_dept (university_name, department_name),
    -- ② 내 라인 찾기(Bottom-Up): percentile70 BETWEEN 범위검색이 0.1초 내 수행되도록 인덱스
    KEY idx_percentile70 (percentile70)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`idx_percentile70` 단일 컬럼 B-Tree 인덱스가 BETWEEN 레인지 스캔의 핵심. 결과를 `percentile70 DESC`로 정렬해 반환하면 인덱스 순서를 그대로 활용(filesort 회피).

### CSV → 테이블 적재 (LOAD DATA)
```sql
LOAD DATA LOCAL INFILE 'integrated_jeongsi_results_2025.csv'
INTO TABLE jeongsi_cutoffs
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES   -- 헤더(utf-8-sig BOM 주의: 첫 컬럼 region 매핑 확인)
(region, university_name, admission_group, department_name,
 @recruit, @comp, @rank, @ko, @ma, @tg, percentile70,
 ratio_kor, ratio_math, ratio_eng, ratio_tam)
SET recruit_count    = NULLIF(@recruit,''),
    competition_rate = NULLIF(@comp,''),
    additional_rank  = NULLIF(@rank,''),
    korean_cut70     = NULLIF(@ko,''),
    math_cut70       = NULLIF(@ma,''),
    tamgu_cut70      = NULLIF(@tg,'');
```

---

## 3. JPA 엔티티

```java
@Entity
@Table(name = "jeongsi_cutoffs", indexes = {
    @Index(name = "idx_uni_dept",     columnList = "universityName,departmentName"),
    @Index(name = "idx_percentile70", columnList = "percentile70")   // 내 라인 찾기 BETWEEN용
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JeongsiCutoff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)  private String region;
    @Column(length = 100) private String universityName;
    @Column(length = 10)  private String admissionGroup;
    @Column(length = 100) private String departmentName;

    private Integer recruitCount;
    private Double  competitionRate;
    private Integer additionalRank;

    private Double koreanCut70;     // nullable (Format A 미제공)
    private Double mathCut70;
    private Double tamguCut70;
    private Double percentile70;    // ★메인 인덱스 (수능평균 70%컷)

    private Double ratioKor, ratioMath, ratioEng, ratioTam;
}
```

---

## 4. Repository 쿼리 메소드 초안

```java
public interface JeongsiCutoffRepository extends JpaRepository<JeongsiCutoff, Long> {

    // ── ① 목표 대학 매칭(Top-Down): 대학+학과 단건 조회 (idx_uni_dept 사용)
    Optional<JeongsiCutoff> findByUniversityNameAndDepartmentName(String univ, String dept);

    // ── ② 내 라인 찾기(Bottom-Up): percentile70 BETWEEN 범위검색 (idx_percentile70 사용)
    // 파생 쿼리 — 메소드명만으로 BETWEEN + 정렬 생성
    List<JeongsiCutoff> findByPercentile70BetweenOrderByPercentile70Desc(double low, double high);

    // 또는 명시적 JPQL (limit·가독성 제어가 필요할 때)
    @Query("""
        SELECT j FROM JeongsiCutoff j
        WHERE j.percentile70 BETWEEN :low AND :high
        ORDER BY j.percentile70 DESC
        """)
    List<JeongsiCutoff> findMyLine(@Param("low") double low, @Param("high") double high);
}
```

### 호출 규약 (소신선/안정선)
`gap = 내백분위 − 컷`. 사용자 예시 "−2점 소신선 ~ +5점 안정선"을 그대로 매핑:
- **소신선(reach)**: gap = −2 → 컷이 내 점수보다 2 높음 → 상한 `high = score + 2`
- **안정선(safe)**: gap = +5 → 컷이 내 점수보다 5 낮음 → 하한 `low = score − 5`

```java
// 내 평균 백분위 score 기준 라인 찾기
double low  = score - safeMargin;   // 기본 5 (안정선)
double high = score + reachMargin;  // 기본 2 (소신선)
List<JeongsiCutoff> line = repo.findByPercentile70BetweenOrderByPercentile70Desc(low, high);
// 각 결과는 Signal.ofDomesticGap(score - cut) 로 안정/적정/소신/위험 라벨링
```

H2(현 개발)·MySQL(운영) 모두 동일 JPQL 동작. 인덱스 덕에 전국 1,400+행에서도 레인지 스캔만 수행 → 0.1초 내 응답.

---

## 5. 현 코드베이스 적용 메모
- 현재 앱은 슬림 CSV `kcue_domestic_cutoffs.csv`(universityName/departmentName/ratio*/cutoffPercentile70)를 `DomesticUniversityCutoff`로 적재 중. 위 풍부 스키마는 `JeongsiCutoff`로 **승격 마이그레이션** 시 청사진.
- 우선 BETWEEN 핵심기능은 기존 `cutoffPercentile70` 위에서 즉시 구현 가능(엔티티 확장 없이도). 모집인원·경쟁률·추합 노출이 필요할 때 위 스키마로 확장.
- 반영비율은 추정치이므로 합격예측 정밀화가 아니라 **0단계 위치 스크리닝** 용도로만 사용(README 신뢰 범위 준수).
