# Fallback Seed Data

이 폴더의 CSV 3종은 **부팅 안전망**(SOP 3.3 Fallback)입니다.

- 시스템이 빈 DB로 부팅되어 진단 API 가 5xx 를 뱉는 사고를 막기 위한 최소 시드.
- 실제 운영 데이터는 `backend/data-staging/*.csv` (사용자 배치) 또는 `KcueSyncService` (대교협 API) 가 부팅 직후 비동기로 UPSERT 합니다.
- **운영 배포 전 본 시드만으로 진단 결과를 신뢰해선 안 됩니다.** `SECURITY_HANDOFF.md` 항목 6 참조.
