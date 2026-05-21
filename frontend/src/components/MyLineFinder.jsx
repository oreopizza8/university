import { useMemo, useState } from 'react';
import { findMyLine } from '../api/diagnostic.js';
import { SIGNAL_STYLE, SIGNAL_ORDER } from '../constants/signal.js';

export default function MyLineFinder() {
  const [avg, setAvg] = useState('');
  const [safe, setSafe] = useState(5);
  const [reach, setReach] = useState(2);
  const [res, setRes] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // 결과 보기 옵션 (클라이언트 처리)
  const [regionFilter, setRegionFilter] = useState('');
  const [groupFilter, setGroupFilter] = useState('');
  const [sortBy, setSortBy] = useState('near');
  const [showSubjects, setShowSubjects] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const v = parseFloat(avg);
    if (isNaN(v) || v < 0 || v > 100) { setError('국/수/탐 백분위 평균(0~100)을 입력하세요.'); return; }
    setLoading(true); setError(null);
    try {
      const data = await findMyLine({ avgPercentile: v, safeMargin: Number(safe), reachMargin: Number(reach) });
      setRes(data);
      setRegionFilter(''); setGroupFilter(''); setSortBy('near');
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const items = res?.items || [];
  const regionOptions = useMemo(
    () => [...new Set(items.map((i) => i.region).filter(Boolean))].sort(), [items]);
  const groupOptions = useMemo(
    () => [...new Set(items.map((i) => i.admissionGroup).filter(Boolean))].sort(), [items]);

  const view = useMemo(() => {
    let arr = items.filter((i) =>
      (!regionFilter || i.region === regionFilter) &&
      (!groupFilter || i.admissionGroup === groupFilter));
    const inf = Number.POSITIVE_INFINITY;
    if (sortBy === 'cutDesc') arr = [...arr].sort((a, b) => b.cutoffPercentile70 - a.cutoffPercentile70);
    else if (sortBy === 'cutAsc') arr = [...arr].sort((a, b) => a.cutoffPercentile70 - b.cutoffPercentile70);
    else if (sortBy === 'compAsc') arr = [...arr].sort((a, b) => (a.competitionRate ?? inf) - (b.competitionRate ?? inf));
    // 'near' = 백엔드 반환순(내 점수 근접순) 유지
    return arr;
  }, [items, regionFilter, groupFilter, sortBy]);

  const groups = useMemo(() => {
    const g = {};
    view.forEach((it) => { (g[it.signal] ||= []).push(it); });
    return g;
  }, [view]);

  const ctrl = 'rounded-lg border border-slate-300 px-2 py-1.5 text-sm bg-white';

  return (
    <div className="space-y-6">
      <form onSubmit={submit} className="bg-white rounded-2xl shadow p-6 space-y-4">
        <div>
          <h2 className="text-lg font-bold text-slate-900">갈 수 있는 대학</h2>
          <p className="text-xs text-slate-500">
            내 국/수/탐 백분위 평균을 입력하면, 그 점수 주변대(소신선~안정선)의 합격 컷을 가진 전국 학과를 찾아줍니다.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <label className="block">
            <span className="text-sm font-semibold text-slate-700">국/수/탐 백분위 평균</span>
            <input type="number" step="0.1" min="0" max="100" value={avg}
              onChange={(e) => setAvg(e.target.value)} placeholder="예: 88.5"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-slate-700">안정선 마진(−)</span>
            <input type="number" step="1" min="0" max="30" value={safe}
              onChange={(e) => setSafe(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <span className="text-[11px] text-slate-400">컷이 내 점수보다 이만큼 낮은 곳까지</span>
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-slate-700">소신선 마진(+)</span>
            <input type="number" step="1" min="0" max="30" value={reach}
              onChange={(e) => setReach(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <span className="text-[11px] text-slate-400">컷이 내 점수보다 이만큼 높은 곳까지</span>
          </label>
        </div>

        <button type="submit" disabled={loading}
          className="w-full bg-slate-900 text-white font-semibold rounded-lg py-2.5 text-sm disabled:opacity-50">
          {loading ? '검색 중…' : '갈 수 있는 대학 찾기'}
        </button>
      </form>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 text-sm">{error}</div>
      )}

      {res && (
        <div className="bg-white rounded-2xl shadow p-6 space-y-5">
          <div className="flex items-baseline justify-between flex-wrap gap-2">
            <h3 className="text-base font-bold text-slate-900">
              내 백분위 {res.avgPercentile} · 컷 {res.rangeLow}~{res.rangeHigh} 범위
            </h3>
            <span className="text-sm text-slate-500">
              범위 내 {res.totalCount}개
              {res.items.length < res.totalCount && ` · 근접순 ${res.items.length}개`}
              {view.length !== res.items.length && ` · 필터 ${view.length}개`}
            </span>
          </div>

          {res.totalCount === 0 ? (
            <p className="text-sm text-slate-500">해당 범위에 등록된 입결 학과가 없습니다. 마진을 넓혀보세요.</p>
          ) : (
            <div className="flex flex-wrap items-center gap-2 border-y border-slate-100 py-3">
              <select className={ctrl} value={regionFilter} onChange={(e) => setRegionFilter(e.target.value)}>
                <option value="">전체 지역</option>
                {regionOptions.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
              <select className={ctrl} value={groupFilter} onChange={(e) => setGroupFilter(e.target.value)}>
                <option value="">전체 모집군</option>
                {groupOptions.map((g) => <option key={g} value={g}>{g}</option>)}
              </select>
              <select className={ctrl} value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                <option value="near">내 점수 근접순</option>
                <option value="cutDesc">컷 높은순</option>
                <option value="cutAsc">컷 낮은순</option>
                <option value="compAsc">경쟁률 낮은순</option>
              </select>
              <label className="flex items-center gap-1.5 text-sm text-slate-600 ml-auto cursor-pointer select-none">
                <input type="checkbox" checked={showSubjects} onChange={(e) => setShowSubjects(e.target.checked)} />
                과목별 컷 표시
              </label>
            </div>
          )}

          {SIGNAL_ORDER.filter((s) => groups[s]?.length).map((s) => {
            const st = SIGNAL_STYLE[s];
            return (
              <div key={s} className="space-y-2">
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${st.dot}`} />
                  <span className={`text-sm font-bold ${st.text}`}>{s}</span>
                  <span className="text-xs text-slate-400">{groups[s].length}개</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {groups[s].map((it, i) => (
                    <div key={i} className={`rounded-lg px-3 py-2 text-sm ${st.bg}`}>
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-slate-800 truncate">
                          {it.universityName} <span className="text-slate-500">{it.departmentName}</span>
                          {it.admissionGroup && <span className="text-slate-400 text-xs ml-1">{it.admissionGroup}</span>}
                        </span>
                        <span className="text-slate-600 whitespace-nowrap ml-2">
                          컷 {it.cutoffPercentile70} · {it.gap >= 0 ? '+' : ''}{it.gap}
                        </span>
                      </div>
                      {(it.recruitCount != null || it.competitionRate != null || it.additionalRank != null || it.region) && (
                        <div className="text-[11px] text-slate-400 mt-0.5 flex gap-2 flex-wrap">
                          {it.region && <span>{it.region}</span>}
                          {it.recruitCount != null && <span>모집 {it.recruitCount}명</span>}
                          {it.competitionRate != null && <span>경쟁 {it.competitionRate}:1</span>}
                          {it.additionalRank != null && <span>추합 {it.additionalRank}</span>}
                        </div>
                      )}
                      {showSubjects && (it.koreanCut70 != null || it.mathCut70 != null || it.tamguCut70 != null) && (
                        <div className="text-[11px] text-slate-500 mt-1 flex gap-3 border-t border-white/60 pt-1">
                          <span>국 {it.koreanCut70 ?? '–'}</span>
                          <span>수 {it.mathCut70 ?? '–'}</span>
                          <span>탐 {it.tamguCut70 ?? '–'}</span>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            );
          })}

          <div className="text-xs text-slate-400 border-t border-slate-100 pt-3 space-y-1">
            <p>출처: {res.dataSource}</p>
            <p>{res.note}</p>
          </div>
        </div>
      )}
    </div>
  );
}
