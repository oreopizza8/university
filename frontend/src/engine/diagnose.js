// 진단 엔진 — 백엔드(Spring) 산식을 그대로 포팅한 클라이언트 전용 로직.
// 데이터는 빌드 시 번들된 JSON. 서버/DB 없이 동작(GitHub Pages 정적 배포용).
import DOMESTIC from '../data/domestic.json';
import SUSI from '../data/susi.json';
import GLOBAL from '../data/global.json';

const JEONGSI_SRC = '2025 어디가 공식 정시 입결(70%컷 백분위) · 반영비율은 계열 추정';
const SUSI_SRC = '2025 어디가 공식 수시 내신 입결 기준';

// ===== 점수 변환 (ScoreConversionService) =====
const GRADE_PCT = { 1: 96, 2: 89, 3: 77, 4: 60, 5: 40, 6: 23, 7: 11, 8: 4, 9: 1 };
const clampG = (g) => Math.max(1, Math.min(9, g | 0));
export const gradeToTopPercentile = (g) => GRADE_PCT[clampG(g)];
const koreanGradeToGpa = (g) => Math.max(0, Math.round((4.0 - 0.3 * (clampG(g) - 1)) * 100) / 100);
const percentileSumToSat = (pko, pmath) => {
  const sum = Math.max(0, Math.min(100, pko)) + Math.max(0, Math.min(100, pmath));
  return Math.round((400 + (sum / 200) * 1200) / 10) * 10;
};
// 수시 내신 등급 → 전국 상위 백분위(선형보간)
const susiGradeToPercentile = (g) => {
  const p = [0, 96, 89, 77, 60, 40, 23, 11, 4, 1];
  g = Math.max(1, Math.min(9, g));
  const lo = Math.floor(g), hi = Math.ceil(g);
  if (lo === hi) return p[lo];
  return p[lo] + (p[hi] - p[lo]) * (g - lo);
};

// ===== 신호 (Signal) =====
const SIG = {
  SAFE: { label: '안정', min: 75, max: 95 },
  MODERATE: { label: '적정', min: 55, max: 75 },
  AGGRESSIVE: { label: '소신', min: 30, max: 55 },
  RISKY: { label: '위험', min: 5, max: 30 },
};
const ofDomesticGap = (g) => g >= 0 ? SIG.SAFE : g >= -4 ? SIG.MODERATE : g >= -10 ? SIG.AGGRESSIVE : SIG.RISKY;
const ofSusiGradeGap = (g) => g >= 0.2 ? SIG.SAFE : g >= -0.2 ? SIG.MODERATE : g >= -0.7 ? SIG.AGGRESSIVE : SIG.RISKY;
const ofGlobalComposite = (c) => c >= 0.60 ? SIG.SAFE : c >= 0.40 ? SIG.MODERATE : c >= 0.20 ? SIG.AGGRESSIVE : SIG.RISKY;

const r1 = (v) => Math.round(v * 10) / 10;
const r2 = (v) => Math.round(v * 100) / 100;
const blank = (s) => !s || !String(s).trim();

// ===== 고교 유형 안내 (HighSchoolContextService) — 정적: 사용자가 유형 직접 선택 =====
export const HS_TYPES = ['', '일반고', '특목고', '자사고', '자율고', '특성화고'];
function highSchoolContext(type) {
  switch (type) {
    case '특목고': return { type, message: '특목고는 내신 산출이 불리한 대신 학생부종합·논술·정시에서 강세를 보이는 것이 일반적입니다. 수시 학생부교과(내신 100%) 전형은 불리할 수 있으니, 정시 위치를 주력으로 두고 학종 비교과·세특 경쟁력을 함께 점검하세요.',
      q: ['우리 학교 출신의 최근 학종 합격 사례에서 내신 등급대가 어디까지 내려갔나요?', '이 대학 학종이 교과 세특·전공적합성을 정량 내신보다 얼마나 비중 있게 보나요?'] };
    case '자사고': return { type, message: '자사고는 내신 경쟁이 치열해 학생부교과 전형에서 불리할 수 있으나, 정시·논술에서 강세가 흔합니다. 내신 대비 정시 위치가 좋다면 정시·논술 카드를 우선 검토하세요.',
      q: ['우리 학교의 정시 합격 실적이 수시 실적 대비 어떤가요?'] };
    case '자율고': return { type, message: '자율고는 학교별 편차가 큽니다. 내신 산출 유불리와 정시 실적을 학교 자료로 직접 확인해 수시·정시 비중을 정하세요.',
      q: ['우리 학교 내신 평균이 인근 일반고 대비 어느 수준인가요?'] };
    case '특성화고': return { type, message: '특성화고는 특성화고 전형·동일계 특별전형 등 별도 트랙이 핵심입니다. 일반 정시·수시와 별개로 특별전형 요건을 우선 확인하세요.',
      q: ['특성화고 특별전형/동일계 전형으로 지원 가능한 학과와 정원이 어떻게 되나요?'] };
    case '일반고': return { type, message: '일반고는 수시 학생부교과(내신) 전형 활용 폭이 넓습니다. 모의고사 위치 대비 내신이 더 좋다면 수시 교과 카드를, 반대면 정시를 주력으로 검토하세요.',
      q: ['내 내신 등급이 이 대학 학생부교과 전형의 최근 70%컷과 비교해 어디쯤인가요?'] };
    default: return { type: type || null, message: null, q: [] };
  }
}

function avgTg(req) {
  const t1 = req.tg1Grade, t2 = req.tg2Grade;
  if (t1 == null && t2 == null) return 0;
  if (t1 == null) return gradeToTopPercentile(t2);
  if (t2 == null) return gradeToTopPercentile(t1);
  return (gradeToTopPercentile(t1) + gradeToTopPercentile(t2)) / 2;
}
function avgTgGrade(req) {
  const t1 = req.tg1Grade, t2 = req.tg2Grade;
  if (t1 == null && t2 == null) return null;
  if (t1 == null) return t2;
  if (t2 == null) return t1;
  return Math.round((t1 + t2) / 2);
}

// ===== 솔루션: 과목 진단 / 민감도 (SolutionService) =====
function subjectInsight(name, grade, pct, weight, weighted) {
  let role = pct >= weighted + 4 ? '강점' : pct <= weighted - 4 ? '취약' : '보통';
  const w = weight || 0;
  let note;
  if (role === '취약' && w >= 0.25) note = `반영 ${Math.round(w * 100)}% 핵심 과목인데 약점 — 최우선 보완 대상`;
  else if (role === '취약') note = '평균 대비 발목을 잡는 과목';
  else if (role === '강점' && w >= 0.25) note = `반영 ${Math.round(w * 100)}% 비중 과목에서 강점 — 합격 견인`;
  else if (role === '강점') note = '평균을 끌어올리는 과목';
  else note = '평균 수준';
  return { subject: name, grade, percentile: r1(pct), weight, role, note };
}
function lever(out, name, grade, pct, weight, weighted, cut70, cur) {
  if (!weight || weight <= 0 || grade == null || grade <= 1) return;
  const newPct = gradeToTopPercentile(grade - 1);
  const nw = weighted - pct * weight + newPct * weight;
  const ns = ofDomesticGap(nw - cut70);
  const tier = ns.label !== cur.label ? `판정 ${cur.label}→${ns.label} ✅` : `판정 ${cur.label} 유지`;
  out.push(`· ${name} ${grade}→${grade - 1}등급: 가중 백분위 ${r1(nw)} (${(nw - weighted >= 0 ? '+' : '')}${r1(nw - weighted)}pp), ${tier}`);
}
function signalMeaning(s) {
  return s === SIG.SAFE ? '합격 안정권입니다.' : s === SIG.MODERATE ? '합격은 가능하나 안심하긴 이른 적정권입니다.'
    : s === SIG.AGGRESSIVE ? '합격 가능성이 낮은 소신권입니다.' : '현재 점수로는 위험권입니다.';
}
function pickLines(list, target, limit = 3) {
  const out = [];
  for (const c of list) {
    if (!c.u || c.u.startsWith('(')) continue;
    if (target && c.u === target.u && c.d === target.d) continue;
    if (c.cut == null) continue;
    out.push(`${c.u} ${c.d}(컷 ${Math.round(c.cut)})`);
    if (out.length >= limit) break;
  }
  return out;
}
function portfolio(target, weighted) {
  const safe = DOMESTIC.filter((c) => c.cut != null && c.cut <= weighted).sort((a, b) => b.cut - a.cut);
  const reach = DOMESTIC.filter((c) => c.cut != null && c.cut > weighted).sort((a, b) => a.cut - b.cut);
  const lines = [];
  const s = pickLines(safe, target), r = pickLines(reach, target);
  if (s.length) lines.push('안정 지원 후보(내 점수가 컷 이상): ' + s.join(', '));
  if (r.length) lines.push('소신 지원 후보(컷이 내 점수보다 높음): ' + r.join(', '));
  if (!lines.length) lines.push('같은 점수대 분산 지원을 위해 안정·적정·소신 3개 라인으로 카드를 나누세요.');
  return lines;
}

// ===== 국내 정시 =====
function diagnoseDomestic(req) {
  const pKo = gradeToTopPercentile(req.koreanGrade), pMath = gradeToTopPercentile(req.mathGrade),
    pEn = gradeToTopPercentile(req.englishGrade), pTg = avgTg(req);
  const hs = highSchoolContext(req.highSchoolType);
  const guide = [
    '최근 3개년 정시 70% 컷이 우리 학교 평균보다 어떻게 움직였나요?',
    '수시 내신 반영 방식(전과목/주요과목)이 정확히 어떻게 되나요?',
    '표준점수·백분위 중 어떤 지표를 환산식에 쓰나요? 가중치 산식 공개 부탁드립니다.',
    '이 점수대 학생이 작년에 실제로 합격/불합격한 비율 데이터 보여주실 수 있나요?', ...hs.q];

  const cutoff = DOMESTIC.find((c) => c.u === req.targetUniversity && c.d === req.targetDepartment);

  if (!cutoff || cutoff.cut == null) {
    const weighted = pKo * 0.25 + pMath * 0.30 + pEn * 0.20 + pTg * 0.25;
    const uni = blank(req.targetUniversity) ? '입력하신 대학' : req.targetUniversity;
    const dept = blank(req.targetDepartment) ? '' : ' ' + req.targetDepartment;
    const subjects = [
      subjectInsight('국어', req.koreanGrade, pKo, null, weighted),
      subjectInsight('수학', req.mathGrade, pMath, null, weighted),
      subjectInsight('영어', req.englishGrade, pEn, null, weighted),
      subjectInsight('탐구', avgTgGrade(req), pTg, null, weighted)];
    const next = ['합격선 비교는 불가능한 상태라, 아래는 한 등급 향상 시 전국 백분위 변화입니다.'];
    const DR = [0.25, 0.30, 0.20, 0.25];
    const ndLever = (nm, g, p, w) => { if (g != null && g > 1) next.push(`· ${nm} ${g}→${g - 1}등급: 전국 백분위 ${r1(weighted + (gradeToTopPercentile(g - 1) - p) * w)} (${(((gradeToTopPercentile(g - 1) - p) * w) >= 0 ? '+' : '')}${r1((gradeToTopPercentile(g - 1) - p) * w)}pp)`); };
    ndLever('국어', req.koreanGrade, pKo, DR[0]); ndLever('수학', req.mathGrade, pMath, DR[1]);
    ndLever('영어', req.englishGrade, pEn, DR[2]); ndLever('탐구', avgTgGrade(req), pTg, DR[3]);
    const safe = pickLines(DOMESTIC.filter((c) => c.cut != null && c.cut <= weighted).sort((a, b) => b.cut - a.cut), null);
    const reach = pickLines(DOMESTIC.filter((c) => c.cut != null && c.cut > weighted).sort((a, b) => a.cut - b.cut), null);
    const strategy = ['정확한 합격 판정을 위해, 목표 대학·학과의 최근 정시 70%컷(대입정보포털 \'어디가\' 또는 대학 발표)을 확인하세요.',
      '아래는 내 전국 백분위 기준으로 입결이 등록된 학과 중 비슷한 라인입니다(참고용).'];
    if (safe.length) strategy.push('내 점수 이하 컷: ' + safe.join(', '));
    if (reach.length) strategy.push('내 점수 초과 컷: ' + reach.join(', '));
    return {
      mode: 'DOMESTIC', cutoffAvailable: false, nationalPercentile: r2(weighted),
      targetUniversity: uni, targetDepartment: dept.trim(),
      summary: '이 대학·학과의 정시 70%컷 입결 데이터가 아직 없어 합격선 판정은 보류했습니다. 아래 전국 백분위와 일반 조언을 참고하세요.',
      guideQuestions: guide, highSchoolType: hs.type, highSchoolContext: hs.message,
      solution: { rationale: `${uni}${dept}의 정시 70%컷 입결 데이터가 아직 없어 합격 가능성은 판정하지 못했습니다. 대신 전국 백분위(약 ${r1(weighted)}, 상위 ${r1(Math.max(0, 100 - weighted))}%)와 과목 강약점으로 방향을 제시합니다.`, subjects, nextSteps: next, strategy },
      dataSource: '입결 데이터 미보유 · 전국 백분위는 표준 반영비율 기준 근사',
    };
  }

  const weighted = pKo * cutoff.rk + pMath * cutoff.rm + pEn * cutoff.re + pTg * cutoff.rt;
  const gap = weighted - cutoff.cut;
  const sig = ofDomesticGap(gap);
  const tgGrade = avgTgGrade(req);
  const subjects = [
    subjectInsight('국어', req.koreanGrade, pKo, cutoff.rk, weighted),
    subjectInsight('수학', req.mathGrade, pMath, cutoff.rm, weighted),
    subjectInsight('영어', req.englishGrade, pEn, cutoff.re, weighted),
    subjectInsight('탐구', tgGrade, pTg, cutoff.rt, weighted)];
  const next = [gap < 0 ? `목표컷까지 가중 백분위 ${r1(-gap)}pp 부족합니다. 가장 효율적인 보완 과목은 아래와 같습니다.` : '이미 목표컷 위입니다. 아래는 한 단계 더 끌어올렸을 때의 변화입니다.'];
  lever(next, '국어', req.koreanGrade, pKo, cutoff.rk, weighted, cutoff.cut, sig);
  lever(next, '수학', req.mathGrade, pMath, cutoff.rm, weighted, cutoff.cut, sig);
  lever(next, '영어', req.englishGrade, pEn, cutoff.re, weighted, cutoff.cut, sig);
  if (tgGrade != null) lever(next, '탐구', tgGrade, pTg, cutoff.rt, weighted, cutoff.cut, sig);
  const track = gap >= 0 ? '정시 기준 합격선 위입니다. 같은 점수대에서 상향 1~2장을 더 노려볼 여지가 있습니다.'
    : gap >= -4 ? '정시로는 적정~경계권. 정시 한 장에 올인하기보다 수시 카드를 병행해 안전망을 두세요.'
    : '정시만으로는 무리한 라인입니다. 수시(학생부·논술)와 점수 향상 둘 다 필요합니다.';
  return {
    mode: 'DOMESTIC', cutoffAvailable: true, nationalPercentile: r2(weighted),
    signal: sig.label, probabilityMin: sig.min, probabilityMax: sig.max,
    targetUniversity: cutoff.u, targetDepartment: cutoff.d, targetCutoffPercentile: cutoff.cut,
    summary: `반영비율 가중 백분위 ${r1(weighted)} / 목표컷 70% 컷 ${r1(cutoff.cut)} → 격차 ${r1(gap)}pp`,
    guideQuestions: guide, highSchoolType: hs.type, highSchoolContext: hs.message,
    solution: {
      rationale: `가중 백분위 ${r1(weighted)}은 ${cutoff.u} ${cutoff.d} 70%컷 ${r1(cutoff.cut)} 대비 ${(gap >= 0 ? '+' : '')}${r1(gap)}pp입니다. 70%컷은 합격자의 70%가 이 점수 이상인 안정선이라, ${signalMeaning(sig)}`,
      subjects, nextSteps: next, strategy: [track, ...portfolio(cutoff, weighted)],
    },
    dataSource: cutoff.src || JEONGSI_SRC,
  };
}

// ===== 국내 수시 =====
function diagnoseSusi(req) {
  const hs = highSchoolContext(req.highSchoolType);
  const guide = ['학생부교과 반영 방식(전과목/주요과목/학년별 가중)이 정확히 어떻게 되나요?',
    '진로선택과목·전문교과 반영 방식과 가산점이 있나요?', '수능 최저학력기준이 있나요? 있다면 충족 가능성은요?',
    '이 내신대 학생의 작년 실제 합격/불합격 비율 데이터를 볼 수 있나요?', ...hs.q];
  const naesin = req.naesinAverage;
  const base = (extra) => ({ mode: 'SUSI', targetUniversity: req.targetUniversity || '', targetDepartment: req.targetDepartment || '', highSchoolType: hs.type, highSchoolContext: hs.message, guideQuestions: guide, dataSource: SUSI_SRC, ...extra });
  if (naesin == null) return base({ cutoffAvailable: false, summary: '수시 진단에는 내신 평균등급 입력이 필요합니다.' });
  const valid = SUSI.filter((s) => s.u === req.targetUniversity && s.d === req.targetDepartment && s.g70 != null && s.g70 >= 1 && s.g70 <= 9);
  if (!valid.length) return base({ cutoffAvailable: false, nationalPercentile: r1(susiGradeToPercentile(naesin)),
    summary: '이 대학·학과의 수시 내신 70%컷 데이터가 아직 없어 합격선 판정은 보류했습니다. 내신 상위 위치만 참고하세요.',
    solution: { rationale: `입력하신 대학·학과의 수시 내신 70%컷 데이터가 없어 합격 판정은 보류했습니다. 내신 평균 ${r2(naesin)}등급의 전국 상위 위치만 참고하세요.`, subjects: [], nextSteps: ['목표 대학·학과의 최근 수시 교과 70%컷 등급(대입정보포털 \'어디가\')을 확인해 보세요.'], strategy: ['교과 외 학생부종합·논술 전형의 가능성도 함께 검토하세요.'] } });
  valid.sort((a, b) => a.g70 - b.g70);
  const pick = valid[Math.floor(valid.length / 2)];
  const cut = pick.g70, gap = cut - naesin, sig = ofSusiGradeGap(gap);
  const at = pick.at || '교과전형';
  const next = [gap < 0 ? `목표컷까지 내신 ${r2(-gap)}등급 부족합니다. 남은 학기 주요과목 등급을 끌어올리면 격차가 줄어듭니다.` : '이미 70%컷 위입니다. 내신 외에 수능최저·면접 요건을 점검하세요.'];
  if (valid.length > 1) next.push(`같은 학과 전형별 70%컷이 ${r2(valid[0].g70)}~${r2(valid[valid.length - 1].g70)}등급으로 분포합니다. 전형별 반영방식이 다르니 유리한 전형을 찾으세요.`);
  const strat = [gap >= 0.2 ? '내신 안정권입니다. 수시 교과를 안정 카드로 두고 상향 1~2장을 더 노려보세요.' : gap >= -0.2 ? '경계선입니다. 교과 한 장에 의존하지 말고 종합·논술·정시와 분산하세요.' : '교과로는 상향입니다. 학생부종합·논술 등 내신 외 요소가 강한 전형을 함께 검토하세요.',
    '수능 최저학력기준 충족 여부가 실질 합격선을 좌우합니다. 최저부터 확인하세요.'];
  return base({ cutoffAvailable: true, targetUniversity: pick.u, targetDepartment: pick.d, admissionType: pick.at,
    targetCutoffGrade: cut, nationalPercentile: r1(susiGradeToPercentile(naesin)),
    signal: sig.label, probabilityMin: sig.min, probabilityMax: sig.max,
    summary: `내신 평균 ${r2(naesin)}등급 / ${at} 70%컷 ${r2(cut)}등급 → 격차 ${(gap >= 0 ? '+' : '')}${r2(gap)}등급`,
    dataSource: SUSI_SRC + ' · 전형 다수 시 중앙값 기준',
    solution: { rationale: `내신 평균 ${r2(naesin)}등급은 ${pick.u} ${at} 70%컷 ${r2(cut)}등급 대비 ${(gap >= 0 ? '+' : '')}${r2(gap)}등급입니다. 70%컷은 합격자의 70%가 이 등급 이상인 안정선이라, ${signalMeaning(sig)}`, subjects: [], nextSteps: next, strategy: strat } });
}

// ===== 해외 유학 =====
function positionRatio(v, lo, hi) { if (hi <= lo) return 0.5; return Math.max(0, Math.min(1, (v - lo) / (hi - lo))); }
function positionWord(p) { return p <= 0 ? '하단(25%컷 이하)' : p < 0.5 ? '하위 구간' : p < 0.75 ? '중상위 구간' : '상단(75%컷 이상)'; }
function diagnoseGlobal(req) {
  const mainGrade = Math.round((req.koreanGrade + req.mathGrade + req.englishGrade) / 3);
  const gpa = koreanGradeToGpa(mainGrade);
  const pKo = gradeToTopPercentile(req.koreanGrade), pMath = gradeToTopPercentile(req.mathGrade);
  const sat = percentileSumToSat(pKo, pMath);
  const guide = ['이 대학의 최근 3년 합격자 CDS(SAT/GPA 25-75) 자료 보여주실 수 있나요?',
    '한국 내신을 GPA로 환산하는 우리 유학원만의 공식이 있나요? 공식 산식 공개 부탁드립니다.',
    '에세이/추천서 평가 비중과 정량 점수의 비중을 어떻게 나누나요?',
    '이 학교에 보낸 작년 실제 합격 학생 수와 평균 스펙은 어떻게 되나요?'];
  const c = GLOBAL.find((x) => x.u === req.targetUniversity);
  if (!c) {
    const name = blank(req.targetUniversity) ? '(미입력 대학)' : req.targetUniversity;
    return { mode: 'GLOBAL', cutoffAvailable: false, convertedGpa: gpa, convertedSat: sat,
      targetUniversity: name, targetDepartment: req.targetDepartment,
      summary: `이 대학의 CDS(SAT/GPA 25-75) 데이터가 아직 없어 합격선 판정은 보류했습니다. 환산 GPA ${r2(gpa)} / 가상 SAT ${sat} 는 등급 기반 추정치이니 참고만 하세요.`,
      guideQuestions: guide, dataSource: 'CDS 데이터 미보유 · 환산 GPA/SAT는 등급 기반 추정' };
  }
  const r75 = c.sm75 + c.sr75, r25 = c.sm25 + c.sr25;
  const satPos = positionRatio(sat, r25, r75);
  const gpaPos = positionRatio(gpa * 100, c.gpa * 100 - 20, c.gpa * 100 + 20);
  const composite = (satPos + gpaPos) / 2;
  const sig = ofGlobalComposite(composite);
  const targetSat = Math.round((r25 + r75) / 2 / 10) * 10;
  const next = [satPos <= gpaPos
    ? `두 지표 중 SAT가 상대적 약점입니다. CDS 중앙값(약 ${targetSat})까지 올리면 합격선 진입에 가장 직접적입니다.`
    : `두 지표 중 GPA가 상대적 약점입니다. 내신 관리로 평균 ${r2(c.gpa)}에 근접시키는 것이 우선입니다.`,
    '정량 점수 외에 이 학교가 중시하는 에세이·추천서·비교과로 격차를 일부 메울 수 있는지 점검하세요.'];
  const reach = sig === SIG.SAFE ? `${c.u}은 정량상 안정권이나, 미국 입시 특성상 안심 금물 — match/safety도 반드시 함께 지원하세요.`
    : sig === SIG.MODERATE ? `${c.u}은 match(적정) 라인입니다. safety 1~2곳을 반드시 추가하세요.`
    : sig === SIG.AGGRESSIVE ? `${c.u}은 소신(reach) 라인입니다. match·safety 비중을 더 두세요.`
    : `${c.u}은 현재 정량으로 깊은 reach입니다. 비교과·에세이 강점이 없다면 match/safety 중심으로 재설계하세요.`;
  return {
    mode: 'GLOBAL', cutoffAvailable: true, nationalPercentile: r2(composite * 100),
    signal: sig.label, probabilityMin: sig.min, probabilityMax: sig.max,
    targetUniversity: c.u, targetDepartment: req.targetDepartment, convertedGpa: gpa, convertedSat: sat,
    summary: `환산 GPA ${r2(gpa)} / 가상 SAT ${sat}. 목표교 CDS 25-75 분포 SAT ${r25}~${r75}, 평균 GPA ${r2(c.gpa)} 와 비교`,
    guideQuestions: guide,
    solution: {
      rationale: `환산 SAT ${sat}은 ${c.u} CDS(${r25}~${r75})의 ${positionWord(satPos)}, 환산 GPA ${r2(gpa)}는 평균 ${r2(c.gpa)} ${gpa >= c.gpa ? '이상' : '미만'}입니다. 두 지표 위치를 합쳐 ${signalMeaning(sig)}`,
      subjects: [
        { subject: 'SAT', percentile: r1(satPos * 100), role: satPos >= 0.5 ? '강점' : satPos <= 0.25 ? '취약' : '보통', note: `CDS 25-75(${r25}~${r75}) 대비 위치` },
        { subject: 'GPA', percentile: r1(gpaPos * 100), role: gpaPos >= 0.5 ? '강점' : gpaPos <= 0.25 ? '취약' : '보통', note: `평균 GPA ${r2(c.gpa)} 대비 위치` }],
      nextSteps: next,
      strategy: [reach, '지원 포트폴리오는 reach(목표교) / match(CDS 중앙값이 내 점수와 겹치는 학교) / safety(25%컷이 내 점수 이하인 학교) 3단계로 분산하세요.', '미국 상위권은 정량 점수만으로 합불이 갈리지 않습니다. 활동·전공적합성 스토리를 함께 준비하세요.'],
    },
  };
}

// ===== 라우터 (DiagnosticController) =====
export function diagnose(req) {
  if (req.isGlobal) return diagnoseGlobal(req);
  if (String(req.diagnosisType).toUpperCase() === 'SUSI') return diagnoseSusi(req);
  return diagnoseDomestic(req);
}

// ===== 갈 수 있는 대학 (MyLineService) =====
const SIGNAL_ORDER = ['안정', '적정', '소신', '위험'];
const PER_SIGNAL = 40;
export function findMyLine({ avgPercentile, safeMargin, reachMargin }) {
  const score = avgPercentile;
  const low = r1(score - (safeMargin ?? 5)), high = r1(score + (reachMargin ?? 2));
  const all = DOMESTIC.filter((c) => c.cut != null && c.cut >= low && c.cut <= high).map((c) => {
    const gap = r1(score - c.cut), s = ofDomesticGap(gap);
    return { universityName: c.u, departmentName: c.d, cutoffPercentile70: c.cut, gap, signal: s.label, probMin: s.min, probMax: s.max,
      recruitCount: c.recruit, competitionRate: c.comp, additionalRank: c.rank, koreanCut70: c.ko, mathCut70: c.ma, tamguCut70: c.tg, region: c.region, admissionGroup: c.gun };
  });
  const items = [];
  for (const sg of SIGNAL_ORDER) {
    all.filter((it) => it.signal === sg).sort((a, b) => Math.abs(a.gap) - Math.abs(b.gap)).slice(0, PER_SIGNAL).forEach((it) => items.push(it));
  }
  return { avgPercentile: score, rangeLow: low, rangeHigh: high, totalCount: all.length, items,
    dataSource: '2025 어디가 공식 정시 입결(70%컷 백분위) · 반영비율 계열 추정',
    note: '내 백분위 평균을 기준으로 한 0단계 위치 스크리닝입니다. 합격 확률 정밀예측이 아니며, 실제 지원은 대학별 반영비율·군 조합·표준점수를 반영한 학원 컨설팅으로 검증하세요.' };
}

// ===== 자동완성 (검색) =====
function dedupTop(arr, n = 20) { return arr.slice(0, n); }
export function searchUniversities(isGlobal, q) {
  q = (q || '').trim();
  if (isGlobal) {
    const base = GLOBAL.filter((g) => !q || g.u.includes(q) || (g.country || '').includes(q));
    return dedupTop(base.map((g, i) => ({ id: 'g' + i, universityName: g.u, country: g.country })));
  }
  const base = DOMESTIC.filter((c) => !q || c.u.includes(q) || c.d.includes(q));
  return dedupTop(base.map((c, i) => ({ id: 'd' + i, universityName: c.u, departmentName: c.d })));
}
export function searchSusi(q) {
  q = (q || '').trim();
  const base = SUSI.filter((s) => !q || s.u.includes(q) || s.d.includes(q));
  return dedupTop(base.map((s, i) => ({ id: 's' + i, universityName: s.u, departmentName: s.d, admissionType: s.at })));
}
