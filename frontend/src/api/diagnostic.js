const BASE = import.meta.env.VITE_API_BASE || '';

export async function postDiagnostic(payload) {
  const res = await fetch(`${BASE}/api/diagnostic`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`진단 API 실패: ${res.status}`);
  return res.json();
}

export async function findMyLine(payload) {
  const res = await fetch(`${BASE}/api/diagnostic/my-line`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`갈 수 있는 대학 검색 실패: ${res.status}`);
  return res.json();
}

export async function searchUniversities(isGlobal, q) {
  const path = isGlobal ? 'global' : 'domestic';
  const res = await fetch(`${BASE}/api/diagnostic/universities/${path}?q=${encodeURIComponent(q)}`);
  if (!res.ok) throw new Error(`대학 검색 실패: ${res.status}`);
  return res.json();
}

export async function searchHighSchools(q) {
  const res = await fetch(`${BASE}/api/diagnostic/high-schools?q=${encodeURIComponent(q)}`);
  if (!res.ok) throw new Error(`고교 검색 실패: ${res.status}`);
  return res.json();
}

export async function searchSusi(q) {
  const res = await fetch(`${BASE}/api/diagnostic/universities/susi?q=${encodeURIComponent(q)}`);
  if (!res.ok) throw new Error(`수시 대학 검색 실패: ${res.status}`);
  return res.json();
}
