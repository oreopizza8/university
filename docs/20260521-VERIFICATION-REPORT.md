# 대입 위치 스크리닝 — 결과 정확성 & 데이터 커버리지 검증 리포트

- 작성: 2026-05-21
- 대상: 백엔드 `com.diagnostic` (Spring Boot, H2 인메모리) · 정시/수시 진단 + 갈 수 있는 대학(my-line)
- 목적: **"입력(등급·백분위·내신)을 넣었을 때 결과(가중백분위·신호·컷비교)가 코드 산식대로 정확한가"** 검증. 합불예측 타당성이 아니라 **계산 정확성**.
- 원칙: **할루시네이션 0.** 모든 기대값은 코드 산식(파일:라인)에서 손계산했고, 실제 API 원시응답(`backend/data-staging/_evidence/*.json`)과 대조했다. 추측은 "추측"으로 명시한다.

---

## 0. 검증 방법론 (다른 AI가 재현 가능하도록)

### 환경
- 백엔드 실행: `cd backend && ./gradlew.bat bootRun` → `http://localhost:8080`
- 데이터: `backend/data-staging/kcue_domestic_cutoffs.csv`(정시), `kcue_susi_cutoffs.csv`(수시) — `build_app_data.py` 산출, 부팅 시 적재.
- 호출: `POST /api/diagnostic` (정시/수시), `POST /api/diagnostic/my-line` (갈 수 있는 대학).
- **주의(PowerShell 5.1)**: `Invoke-RestMethod`는 UTF-8 응답을 잘못 디코딩해 한글이 깨진다. 응답 바이트를 `[System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())`로 직접 디코딩해야 신호 라벨이 정확하다. 본 리포트의 결과는 이 방식으로 캡처했다.

### 핵심 불변식 (정시 검증을 결정적으로 만드는 근거)
- 정시 가중백분위 산식: `weighted = pKo·ratioKo + pMath·ratioMath + pEn·ratioEn + pTg·ratioTg` (`DomesticDiagnosticService.java:66-69`).
- **전 학과의 반영비율 합 = 1.0** (데이터 전수 검사 결과 `ratio합 != 1.0` 학과 **0개**).
- 따라서 **국·수·영·탐 등급을 모두 동일한 G로 입력하면** `pKo=pMath=pEn=pTg=gradeToTopPercentile(G)` 이고 `weighted = gradeToTopPercentile(G) × 1.0 = gradeToTopPercentile(G)` — **반영비율과 무관하게 결정적**. 이 성질로 기대값을 확정한다.
- 단, `pTg`는 `tg1Grade`/`tg2Grade`로 계산되며 둘 다 미입력 시 0.0이 된다(`DomesticDiagnosticService.java:105-112`). 따라서 정시 케이스는 `tg1Grade=tg2Grade=G`까지 동일하게 입력한다.

---

## 1. 검증 대상 산식 (코드 출처)

| 산식 | 위치 | 내용 |
|---|---|---|
| 등급→백분위 | `ScoreConversionService.java:30-42` | 1→96, 2→89, 3→77, 4→60, 5→40, 6→23, 7→11, 8→4, 9→1 |
| 정시 가중백분위 | `DomesticDiagnosticService.java:27-30, 66-69` | 입력은 **등급**을 변환해 사용(입력 백분위 필드는 정시 미사용) |
| 정시 격차·신호 | `DomesticDiagnosticService.java:71-72` | `gap = weighted − 70%컷` |
| 신호(정시) | `Signal.java:33-38` (`ofDomesticGap`) | gap≥0 안정 / ≥−4 적정 / ≥−10 소신 / else 위험 |
| 수시 컷 선택 | `SusiDiagnosticService.java:58-59` | 학과 다전형 시 70%컷등급 **중앙값** 전형 |
| 수시 격차·신호 | `SusiDiagnosticService.java:61-62` | `gap = 70%컷등급 − 내신` |
| 신호(수시) | `Signal.java:54-59` (`ofSusiGradeGap`) | gap≥0.2 안정 / ≥−0.2 적정 / ≥−0.7 소신 / else 위험 |
| 내신→백분위 | `SusiDiagnosticService.java:148-155` | 등급 구간 선형보간 (배열 {0,96,89,77,60,40,23,11,4,1}) |
| my-line 범위 | `MyLineService.java` | `low=점수−safe, high=점수+reach`, 컷이 [low,high]인 학과, `gap=점수−컷` |

---

## 2. 정시 계산 정확성 (PASS 4/4)

입력: 국·수·영·탐(tg1,tg2) 등급을 모두 G로. 기대 `weighted = gradeToTopPercentile(G)`, `gap = weighted − cut`, 신호 = `ofDomesticGap(gap)`.

| 케이스 | 목표 | 입력G | 컷(실응답) | weighted(실응답) | gap | 기대신호 | 실신호 | weighted일치 | 신호일치 |
|---|---|---|---|---|---|---|---|---|---|
| J1 | 연세대 경영학과 | 1 | 93.8 | 96.0 | +2.2 | 안정 | 안정 | ✅ | ✅ |
| J2 | 연세대 경영학과 | 2 | 93.8 | 89.0 | −4.8 | 소신 | 소신 | ✅ | ✅ |
| J3 | 강원대 의예과 | 1 | 97.2 | 96.0 | −1.2 | 적정 | 적정 | ✅ | ✅ |
| J4 | 연세대 의예과 | 2 | 99.5 | 89.0 | −10.5 | 위험 | 위험 | ✅ | ✅ |

- `weighted = gradeToTopPercentile(G)` 성립(96.0/89.0) → 가중 산식·반영비율합=1.0 불변식 실증.
- 신호 4단계(안정/적정/소신/위험) 경계 모두 코드 임계와 일치.
- 원시응답: `_evidence/J1_yonsei_mgmt_G1.json` ~ `J4_yonsei_med_G2.json`

---

## 3. 수시 계산 정확성 (PASS 4/4)

목표: 건국대 경영학과(70%컷등급 = 3.5, 중앙값). 기대 `gap = 3.5 − 내신`, 신호 = `ofSusiGradeGap(gap)`, 전국백분위 = `gradeToPercentile(내신)`.

| 케이스 | 내신 | 컷등급(실응답) | gap | 기대신호 | 실신호 | 전국백분위(실응답) | 기대백분위 | 신호일치 | 백분위일치 |
|---|---|---|---|---|---|---|---|---|---|
| S1 | 2.5 | 3.5 | +1.0 | 안정 | 안정 | 83.0 | 83.0 | ✅ | ✅ |
| S2 | 3.4 | 3.5 | +0.1 | 적정 | 적정 | 70.2 | 70.2 | ✅ | ✅ |
| S3 | 3.8 | 3.5 | −0.3 | 소신 | 소신 | 63.4 | 63.4 | ✅ | ✅ |
| S4 | 4.5 | 3.5 | −1.0 | 위험 | 위험 | 50.0 | 50.0 | ✅ | ✅ |

- 백분위 손계산 예: 내신2.5 → `89+(77−89)×0.5 = 83.0`; 내신3.8 → `77+(60−77)×0.8 = 63.4` (`gradeToPercentile`, 선형보간).
- 원시응답: `_evidence/S1_kku_naesin2.5.json` ~ `S4_kku_naesin4.5.json`

---

## 4. 정직 처리 & my-line 정합성 (PASS)

- **입결 없는 학과** (연세대 "존재하지않는학과", 정시) → `cutoffAvailable=false`, 신호 없음. 가짜 컷으로 "안정" 표출하지 않음. 원시응답 `_evidence/E1_nodata.json`.
- **my-line** (`avgPercentile=88.5, safeMargin=5, reachMargin=2`): `range 83.5~90.5`, total 259, 표시 80.
  - 반환 항목 컷이 전부 [83.5, 90.5] 범위 내 — **범위 이탈 0건**.
  - 각 항목 신호가 gap 부호와 일관(`probMin`을 Signal enum 고정값 프록시로 검산) — **불일치 0건**.
  - 원시응답 `_evidence/MyLine_88.5.json`.

---

## 5. 데이터 커버리지 (정직 보고 — 별도 사안)

> 계산 로직과 무관한 **수집 범위** 문제. 이번 검증 중 발견·일부 수정함.

### 5.1 현황
- 추출 대학 수: **69개 → 100개** (대학명 인식 수정 + 재추출 후). 정시 86개 대학/1,741행, 수시 72개 대학/2,411행.
- 전국 4년제 ~190여 개 중 일부만 커버. **"전국 망라"로 표방 불가.**

### 5.2 누락 원인 2가지 (증거 기반)
- **원인A — 대학명 인식 실패(수정 완료):** 표지 대학명이 `국립부경`+`대학교`로 토막나거나 지역 접두가 분리되면 추출기가 단일단어 규칙으로 못 잡아 대학 전체 누락. `big_univ`에 표지 큰글씨 결합 폴백 추가로 복구. **복구 예시(앱 조회 확인): 국립부경대학교(정시·수시), 부산가톨릭대학교, 부산외국어대학교, 경상국립대학교, 서울시립대학교 등 +31개.**
- **원인B — 어디가 PDF에 이미지/백분위 미수록(소스 한계):** 한양대·중앙대·이화여대 등은 어디가 지역 PDF에서 입결표가 **이미지로 박혀 텍스트 레이어가 없음**(같은 PDF 내 대조):

| 대학 | 데이터 페이지 | 추출 텍스트 글자수 | 표 감지 |
|---|---|---|---|
| 한양대 (서울_4 p37, 어디가) | 데이터 | **0** | 2 |
| 중앙대 (서울_3 p100, 어디가) | 데이터 | **0** | 1 |
| 고려대 (서울_1 p62, 대조군) | 데이터 | **939** | 1 |

  → 어디가 PDF 격자는 감지되나 글자 0 = 이미지. 어디가 단일 소스로는 불가.

- **원인B 후속 — 입학처 2차 소스로 일부 복구(2026-05-21):** 대학 입학처 공시 입시결과를 2차 소스로 추가 → **한양대학교 54개 학과 정시 백분위 복구·적재**(`manual_supplement_jeongsi.csv`, 앱 진단 확인: 한양대 경영 cut 92.12 안정). ⚠️ 단 한양대 공시 단위는 "최종등록자 **상위 80% 평균** 백분위"로 70%컷과 정의가 다름(SOURCES.md 명시).
- **여전히 불가(2차 소스로도):** 중앙대(점수컷 미공시), 한국외대(환산점수 자체척도), 이화여대·홍익대(모집요강만). 이들은 정시 결과를 **백분위로 공개하지 않아** 우리 척도(백분위)로 표현 불가 = 대형학원 컨설팅 영역(자체 환산식·합불표본 필요). OCR로도 해결 안 됨(데이터 가용성 문제).

### 5.3 기타 한계
- 정시 region 14/17 (울산·전북·제주 등 일부 미수록 — Format A/필터/이미지 사유).
- 반영비율(ratio_*)은 모집요강 부재로 **계열 표준 추정치**(국20수35영15탐30 / 국30수25영20탐25 / 기타). 합불 정밀예측 아님.
- **데이터 연도 혼재:** 어디가 추출분 2025학년도, 입학처 보충분(한양대) 2026학년도. 스크리닝 수준에선 허용하나 인지 필요.
- 일부 대학명 잘림 잔존(예: "해양대학교"←한국해양대학교). 정식명 검색 시 누락 가능.

---

## 6. 결론

- **계산 정확성: 통과.** 정시 4/4, 수시 4/4, 정직처리, my-line 정합성 — 입력→코드 산식→실제 응답이 전부 일치. 입력한 등급/내신에 대해 결과(가중백분위·신호·컷비교)는 **로직대로 정확**하다.
- **데이터 커버리지: 부분(100개 대학).** 이름 인식 누락은 수정으로 +31 복구. 단, **이미지 기반 입결표 대학(한양·중앙·홍익·이화·한국외 등)은 OCR 없이는 수집 불가**한 소스 한계.
- 권고: 결과 UI에 커버리지 한계 명시 유지. "전국" 표현 지양. 이미지 대학은 OCR 파이프라인 별도 과제.

---

## 7. 재현 절차 (다른 AI / 검증자용)

```powershell
# 1) 백엔드 기동
cd backend; ./gradlew.bat bootRun     # http://localhost:8080, "Started DiagnosticApplication" 확인

# 2) 정시 한 케이스 (UTF-8 디코딩 필수)
$b='{"isGlobal":false,"diagnosisType":"JEONGSI","koreanGrade":1,"mathGrade":1,"englishGrade":1,"tg1Grade":1,"tg2Grade":1,"targetUniversity":"연세대학교","targetDepartment":"경영학과"}'
$resp=Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/diagnostic" -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($b))
[System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
# 기대: nationalPercentile=96.0, targetCutoffPercentile=93.8, signal=안정

# 3) 원시 증거 대조
#    backend/data-staging/_evidence/*.json (J1~J4, S1~S4, E1_nodata, MyLine_88.5)
```

- 산식 검증: 위 §1의 파일:라인을 직접 열어 본 리포트의 손계산과 대조.
- 커버리지 재현: `backend/data-staging/`에서 `python build_app_data.py` 후 `/api/diagnostic/universities/{domestic,susi}?q=부경` 등으로 복구 확인.
