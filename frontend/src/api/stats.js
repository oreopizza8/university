const BASE = import.meta.env.VITE_API_BASE || '';

export async function fetchDistribution({ mode, university, department }) {
  const params = new URLSearchParams();
  params.set('mode', mode);
  if (university) params.set('university', university);
  if (department) params.set('department', department);

  const res = await fetch(`${BASE}/api/stats/distribution?${params.toString()}`);
  if (!res.ok) throw new Error(`분포 API 실패: ${res.status}`);
  return res.json();
}
