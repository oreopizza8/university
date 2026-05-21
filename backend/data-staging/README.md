# Data Staging 디렉터리

여기에 **운영자가 직접** 배치한 외부 데이터 파일이 들어갑니다.
부팅 시 `StagingDataLoader`가 비동기로 읽어 마스터 테이블을 갱신(UPSERT)합니다.
파일이 없으면 `src/main/resources/data/*.csv`의 Fallback 시드로 동작합니다.

## 갱신 정책 — **100% Manual** (PM 확정 2026-05-19)

- **자동 스케줄러 추가 금지.** 대학 입결 데이터는 연 1회 변동되므로 자동 감시·재변환은 과잉 엔지니어링.
- 운영자가 파일을 갈아 끼우고 **앱을 재시작**하거나 `POST /api/ingest/staging/reload` 를 호출하면 그 시점에 1회 적재.
- xlsx 가 새로 들어오면 `python _convert_xlsx_to_csv.py` 를 운영자가 손으로 1회 실행해 `university_programs.csv` 를 재생성.

## 배치할 파일

### 1. `university_programs.csv` — 대학알리미 학과 마스터 (xlsx 변환 결과)
원본 xlsx (`학교별 교육편제단위 정보_YYYYMMDD기준.xlsx`)를 같은 폴더의 `_convert_xlsx_to_csv.py` 로 1회 변환해 생성.
변환기는 폐지·모집중단 학과를 자동 제외.

### 2. `kcue_domestic_cutoffs.csv` — 대교협 70% 컷 (정시)
헤더 고정:
```
universityName,departmentName,ratioKo,ratioMath,ratioEn,ratioTg,cutoffPercentile70
```

### 3. `us_cds_stats.csv` — 미국·해외 대학 CDS 스크랩 / Scorecard fallback
헤더 고정:
```
country,universityName,satMath25th,satMath75th,satReading25th,satReading75th,avgGpa
```
US College Scorecard 키가 있는 환경에서는 자동 크롤러가 USA 데이터를 갱신. 비-USA(UK/JAPAN/CHINA)는 본 CSV 로만 관리.

### 4. `mock_exam_cutoffs.csv` — (선택) 평가원 모의고사 등급컷
헤더 고정:
```
examYear,examMonth,subjectName,grade,standardScore,percentile,subjectMean,subjectSd
```

## 인코딩
UTF-8 (BOM 없음) 권장. EUC-KR 파일은 변환 후 배치할 것.

## 출처 기록 의무
SOP 보안 핸드오프 항목 6번에 따라, 새 CSV를 배치할 때는 같은 폴더의 `SOURCES.md` 를 갱신하여 출처·수집일자·라이선스를 명기해야 합니다. 미기재 데이터는 운영 배포 금지.

## 수동 재적재 (앱 재시작 없이)
```bash
curl -X POST http://localhost:8080/api/ingest/staging/reload
```
