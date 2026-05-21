# SECURITY HANDOFF — 잔존 보안·운영 리스크

> SOP 3.2 "로컬이라 생략한 보안 사항은 반드시 핸드오프에 남길 것" 원칙에 따라 적재.
> 운영 배포 직전 이 체크리스트를 다시 훑고, 해소된 항목은 항목별 **[해소: YYYY-MM-DD]** 라벨을 붙여 줄을 그어 표시할 것.

## 1. 인증/인가 — 없음 [부분 해소: 2026-05-19]
- **현황:** 본 MVP는 익명 자가진단으로 인증 미적용. `/api/diagnostic/*` 전부 공개.
- **위험:** 진단 API 무한 호출로 BE/DB 부하, 봇 스크래핑.
- **적용된 완화안 (2026-05-19):** `RateLimitFilter` 도입. Bucket4j 8.10.1 인메모리, IP 단위 분당 10/시간당 50 이중 버킷. `POST /api/diagnostic` 만 대상.
- **남은 보강안:**
  - CAPTCHA 1회 (진단 버튼 클릭 시) — 사용자 마찰 vs 봇 방어 tradeoff 검토 후 결정.
  - 멀티 인스턴스 전환 시 인메모리 → Redis 백엔드(Bucket4j-redis) 교체 필요.

## 2. CORS — 와일드카드 금지
- **현황:** `app.allowed-origins` 환경변수 분리. 기본값 `http://localhost:5173` 만 허용.
- **운영 적용 시:** 실제 도메인만 콤마 구분으로 명시. `*` 절대 금지.

## 3. 시크릿 — .env 분리 [점검 강화: 2026-05-19]
- **현황:** `backend/.env` (실제, gitignored) / `backend/.env.example` (템플릿, 커밋됨) 2파일 구조 확정.
- **2026-05-19 인시던트:** `.env.example` 에 실 KCUE/NEIS 키가 직접 입력된 사례 발생 → 즉시 `.env` 로 이전, 템플릿은 placeholder 로 복원. **GitHub 푸시되기 전 차단** 되었으나, 만약 푸시 이력이 있다면 두 키 모두 폐기·재발급 필요.
- **재발 방지:**
  - `.gitignore` 에 `backend/.env`, `frontend/.env` 명시.
  - 추후 pre-commit hook 으로 `*.env.example` 안에 32자+ hex 문자열이 발견되면 차단 검토.
- **DB 비밀번호·외부 API 키 추가 시 반드시 `.env` 경유.**

## 4. 입력 검증 [해소: 2026-05-19]
- **현황:** `DiagnosticRequest` 모든 필드에 `@Min/@Max/@Size + @NotNull` 적용 완료.
  - 등급: 1~9 / 백분위: 0~100 / 학년: 1~3 / 문자열: 최대 100자 (시험종류 30자).
- **에러 응답:** `GlobalExceptionHandler` 가 한글 메시지를 `fields` 맵으로 직렬화.
- **방어 심층:** Service 레이어 `clamp()` 도 제거하지 않고 유지 (Defense-in-depth).

## 5. SQL Injection — JPA로 1차 차단
- **현황:** Repository 메서드명 기반 쿼리만 사용. 네이티브 쿼리 없음.
- **주의:** 추후 Controller 자동완성 검색에서 `q` 파라미터를 네이티브 쿼리로 옮기지 말 것. JPQL/메서드명 기반 유지.

## 6. 데이터 신뢰성 — 시드 데이터 출처 [부분 해소: 2026-05-19]
- **현황 (2026-05-19 갱신):** 3계층 데이터 파이프라인 구축됨.
  - L0 Fallback 시드: `src/main/resources/data/*.csv` — 안전망용 최소 시드.
  - L1 사용자 배치: `backend/data-staging/*.csv` — 부팅 직후 비동기 UPSERT.
  - L2 라이브 API: `KcueSyncService` — KCUE OpenAPI 일 단위 동기화.
- **운영 배포 전 잠금 해제 조건:**
  1. `backend/data-staging/` 에 검수된 KCUE 70%컷 CSV + US CDS CSV 배치 완료.
  2. `data-staging/SOURCES.md` 에 출처·수집일자·라이선스 기재 완료.
  3. `KCUE_API_KEY` 환경변수 설정 → `/api/ingest/status` 가 `OK_*` 반환 확인.
  4. 결과 화면 푸터 디스클레이머 (`DiagnosticResult.jsx` 마지막 줄) 제거 금지.

## 7. 개인정보 비저장 원칙 [확장 적용: 2026-05-19]
- **현황:** 진단 요청 body 자체는 비저장. 통계 적재(`DiagnosticEvent`)는 익명 컬럼만:
  `mode / targetUniversity / targetDepartment / percentileBucket(5단위) / signal / createdAt`.
- **저장 금지 컬럼(영구):** IP, User-Agent, 세션ID, 점수 원본, 학년 외 식별자, 학교명.
- **5단위 버킷팅 이유:** 단일 응답을 fingerprint 화해 역추적당하지 않도록 점수를 양자화.
- **회수 정책:** `diagnostic_events` 는 90일 후 자동 삭제 정책 필요 (TODO: `@Scheduled` cleanup).

## 8. 로그 — 점수/소속 로그 금지
- **현황:** `DataInitializer` 외 비즈니스 로직에 로그 미설치.
- **추가 시 주의:** 사용자 입력 점수·목표 대학을 INFO/DEBUG로 출력하지 말 것. 익명 메트릭(요청 수, 응답 시간)만.

## 9. .gitignore — 미작성
- **TODO:** 본 PR에 `.gitignore` 누락. 다음 PR에서 다음 항목 추가 필수:
  ```
  .env
  *.env.local
  backend/build/
  backend/.gradle/
  frontend/node_modules/
  frontend/dist/
  .idea/
  .vscode/
  ```

## 10. 외부 데이터 동기화 (Fallback) [해소: 2026-05-19]
- **현황:** `KcueSyncService` 가 WebClient 30초 타임아웃 + `onErrorResume` 으로 캐시 유지.
- **검증 방법:** `KCUE_API_KEY` 미설정으로 부팅 → 로그에 `SKIPPED_NO_KEY` → 진단 정상 응답. 의도적 잘못된 키 → `ERROR_FALLBACK_KEPT` → 진단 정상 응답.
- **남은 TODO:** API 응답 스키마가 실제 KCUE 응답 포맷과 일치하는지 키 발급 후 1회 페이로드 캡처 → `KcueResponse` 매핑 점검.

## 11. 의존성 보안 — 자동 스캔 미설정
- **TODO:** GitHub Dependabot 또는 `gradle dependencyCheckAnalyze` 도입. opencsv·spring-boot 둘 다 정기 업데이트 대상.

---

## 12. 통계 표본 부족 시 UX [해소: 2026-05-19]
- **현황:** `DistributionChart` 에 `MIN_SAMPLE=50` 마스킹 분기 추가. 50건 미만 시 점선 박스 + 내 위치 카드로 폴백.
- **남은 점검:** 50 임계 자체가 적절한지는 실데이터 누적 후 재검토.

## 13. KCUE 응답 스키마 검증 [신규 식별: 2026-05-19]
- **위험:** 본 PR의 `KcueResponse` 와 필드명(`ratioKo`, `cutoffPercentile70`)은 가정. 실제 KCUE OpenAPI 응답이 다르면 적재 0건이 조용히 발생.
- **TODO:** 키 발급 직후 raw JSON 1회 캡처 → DTO 매핑 보강. 매핑 실패 건은 별도 카운터로 노출.

## 14. NEIS 응답 스키마 검증 [신규 식별: 2026-05-19]
- **현황:** `NeisSyncService` 가 NEIS 공식 응답 구조(`schoolInfo[1].row[]`) 기준으로 작성됨.
- **남은 점검:** 첫 실행 후 `/api/ingest/status` 의 `neis.lastStatus` 가 `OK_*` 인지, 0건이면 응답 포맷 변화 확인.

## 15. College Scorecard 필드 deprecation [신규 식별: 2026-05-19]
- **위험:** Scorecard API 는 연 단위로 필드명을 손본다(`latest.admissions.sat_scores.*` 경로 변경 이력 있음).
- **TODO:** 분기 1회 응답 포맷 점검. `lastStatus=OK_0` 이 나오면 매핑 실패 알람.

---

**다음 점검일 권장:** MVP 운영 배포 직전, 그리고 분기 1회.
