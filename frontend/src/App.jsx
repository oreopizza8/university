import { useState } from 'react';
import ServiceModeToggle from './components/ServiceModeToggle.jsx';
import DiagnosticInputForm from './components/DiagnosticInputForm.jsx';
import DiagnosticResult from './components/DiagnosticResult.jsx';
import DistributionChart from './components/DistributionChart.jsx';
import MyLineFinder from './components/MyLineFinder.jsx';
import { postDiagnostic } from './api/diagnostic.js';

export default function App() {
  const [isGlobal, setIsGlobal] = useState(false);
  const [susiMode, setSusiMode] = useState(false);
  const [feature, setFeature] = useState('match'); // 'match'(목표대학 매칭) | 'myline'(내 라인 찾기)
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (payload) => {
    setLoading(true);
    setError(null);
    try {
      const data = await postDiagnostic(payload);
      setResult(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen">
      <header className="bg-white border-b border-slate-200">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-xl font-bold text-slate-900">대입·유학 사전 자가점검 키트</h1>
            <p className="text-xs text-slate-500">학원 가기 전 0단계 정량 스크리닝 · 100% 무료</p>
          </div>
          <ServiceModeToggle isGlobal={isGlobal} onChange={setIsGlobal} />
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8 space-y-6">
        {/* 핵심 주기능 선택: 목표 대학 매칭(Top-Down) / 내 라인 찾기(Bottom-Up). 국내 정시 모드 한정. */}
        {!isGlobal && !susiMode && (
          <div className="inline-flex rounded-lg bg-white shadow-sm border border-slate-200 p-1">
            <button type="button" onClick={() => setFeature('match')}
              className={`px-4 py-1.5 rounded-md text-sm font-semibold transition ${feature === 'match' ? 'bg-indigo-600 text-white' : 'text-slate-500'}`}>
              🎯 목표 대학 매칭
            </button>
            <button type="button" onClick={() => setFeature('myline')}
              className={`px-4 py-1.5 rounded-md text-sm font-semibold transition ${feature === 'myline' ? 'bg-indigo-600 text-white' : 'text-slate-500'}`}>
              🔍 갈 수 있는 대학
            </button>
          </div>
        )}

        {!isGlobal && feature === 'match' && (
          <div className="inline-flex rounded-lg bg-white shadow-sm border border-slate-200 p-1">
            <button type="button" onClick={() => setSusiMode(false)}
              className={`px-4 py-1.5 rounded-md text-sm font-semibold transition ${!susiMode ? 'bg-slate-900 text-white' : 'text-slate-500'}`}>
              정시 (수능)
            </button>
            <button type="button" onClick={() => setSusiMode(true)}
              className={`px-4 py-1.5 rounded-md text-sm font-semibold transition ${susiMode ? 'bg-slate-900 text-white' : 'text-slate-500'}`}>
              수시 (내신·교과)
            </button>
          </div>
        )}

        {!isGlobal && feature === 'myline' ? (
          <MyLineFinder />
        ) : (
          <>
            <DiagnosticInputForm isGlobal={isGlobal} susiMode={!isGlobal && susiMode} onSubmit={handleSubmit} loading={loading} />

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 text-sm">
                {error}
              </div>
            )}

            {result && <DiagnosticResult result={result} />}
            {result && <DistributionChart result={result} />}
          </>
        )}
      </main>

      <footer className="max-w-4xl mx-auto px-4 py-6 text-center text-xs text-slate-400 space-y-1.5">
        <p>본 도구는 학원·유학원 마케팅에 휘둘리지 않도록 돕는 정보 비대칭 해소용 사전 진단기입니다.</p>
        <p>
          ⓘ 일부 상위권 대학(중앙대·이화여대·홍익대·한국외대 등)은 정시 결과를 백분위가 아닌 환산점수·표준점수로 공개하거나
          점수컷을 공시하지 않아 현재 라인업에서 제외돼 있습니다. 입학처 공시값 확보 시 순차 보강합니다.
        </p>
      </footer>
    </div>
  );
}
