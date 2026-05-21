// 정적 빌드용: 서버 API 대신 클라이언트 진단 엔진을 호출한다(GitHub Pages 배포).
// 호출부 호환을 위해 Promise를 반환한다.
import * as engine from '../engine/diagnose.js';

export async function postDiagnostic(payload) {
  return engine.diagnose(payload);
}

export async function findMyLine(payload) {
  return engine.findMyLine(payload);
}

export async function searchUniversities(isGlobal, q) {
  return engine.searchUniversities(isGlobal, q);
}

export async function searchSusi(q) {
  return engine.searchSusi(q);
}

// 고교는 정적 버전에서 유형 직접 선택으로 대체 → 자동완성 미사용(빈 결과).
export async function searchHighSchools() {
  return [];
}
