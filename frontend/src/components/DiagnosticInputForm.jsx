import { useEffect, useState } from 'react';
import { searchUniversities, searchSusi } from '../api/diagnostic.js';

const GRADES = [1, 2, 3, 4, 5, 6, 7, 8, 9];
const EXAM_TYPES = ['고1 학평', '고2 학평', '고3 6월 모의', '고3 9월 모의', '수능'];
const HS_TYPES = ['일반고', '특목고', '자사고', '자율고', '특성화고'];

export default function DiagnosticInputForm({ isGlobal, susiMode = false, onSubmit, loading }) {
  const [form, setForm] = useState({
    examType: '고3 6월 모의',
    studentGrade: 3,
    koreanGrade: 2,
    mathGrade: 2,
    englishGrade: 2,
    tg1Grade: 2,
    tg2Grade: 3,
    naesinAverage: 2.5,
    targetUniversity: '',
    targetDepartment: '',
    highSchoolType: '',
  });

  const [suggestions, setSuggestions] = useState([]);
  const [showList, setShowList] = useState(false);
  const [globalUnis, setGlobalUnis] = useState([]);

  useEffect(() => {
    setForm((f) => ({ ...f, targetUniversity: '', targetDepartment: '' }));
    setSuggestions([]);
  }, [isGlobal, susiMode]);

  // 해외 대학 목록(드롭다운용) — 수가 적어 자유입력 대신 선택
  useEffect(() => {
    if (!isGlobal) return;
    searchUniversities(true, '').then(setGlobalUnis).catch(() => {});
  }, [isGlobal]);

  useEffect(() => {
    if (!form.targetUniversity || form.targetUniversity.length < 1) {
      setSuggestions([]);
      return;
    }
    const t = setTimeout(async () => {
      try {
        const res = susiMode ? await searchSusi(form.targetUniversity)
                             : await searchUniversities(isGlobal, form.targetUniversity);
        setSuggestions(res);
      } catch (e) {
        console.error(e);
      }
    }, 200);
    return () => clearTimeout(t);
  }, [form.targetUniversity, isGlobal, susiMode]);

  const update = (k) => (e) => {
    const v = e.target.value;
    setForm({ ...form, [k]: isNaN(Number(v)) || v === '' ? v : Number(v) });
  };

  const pickSuggestion = (s) => {
    setForm({
      ...form,
      targetUniversity: s.universityName,
      targetDepartment: s.departmentName || form.targetDepartment,
    });
    setShowList(false);
  };

  const submit = (e) => {
    e.preventDefault();
    onSubmit({ ...form, isGlobal, diagnosisType: susiMode ? 'SUSI' : 'JEONGSI' });
  };

  return (
    <form onSubmit={submit} className="bg-white rounded-2xl shadow p-6 space-y-5">
      <div className="grid grid-cols-2 gap-4">
        {!susiMode && (
          <Field label="시험 종류">
            <select value={form.examType} onChange={update('examType')} className="input">
              {EXAM_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
        )}
        <Field label="학년">
          <select value={form.studentGrade} onChange={update('studentGrade')} className="input">
            {[1, 2, 3].map((g) => (
              <option key={g} value={g}>고{g}</option>
            ))}
          </select>
        </Field>
      </div>

      {susiMode ? (
        <div>
          <Field label="내신 평균등급 (전 과목, 1.0~9.0)">
            <input
              type="number" min="1" max="9" step="0.01"
              value={form.naesinAverage}
              onChange={update('naesinAverage')}
              placeholder="예: 2.35"
              className="input"
            />
          </Field>
          <p className="text-xs text-slate-400 mt-1">
            ※ 수시 <b>학생부교과(내신)</b> 기준입니다. 수능 최저학력기준·학생부종합(에세이·비교과)은 별도이며 본 진단에 미반영됩니다.
          </p>
        </div>
      ) : (
        <div>
          <p className="text-sm font-semibold text-slate-700 mb-2">과목별 등급 (1~9)</p>
          <div className="grid grid-cols-5 gap-3">
            <GradePicker label="국어" value={form.koreanGrade} onChange={update('koreanGrade')} />
            <GradePicker label="수학" value={form.mathGrade} onChange={update('mathGrade')} />
            <GradePicker label="영어" value={form.englishGrade} onChange={update('englishGrade')} />
            <GradePicker label="탐구1" value={form.tg1Grade} onChange={update('tg1Grade')} />
            <GradePicker label="탐구2" value={form.tg2Grade} onChange={update('tg2Grade')} />
          </div>
        </div>
      )}

      {!isGlobal && (
        <div>
          <Field label="고교 유형 (선택)">
            <select value={form.highSchoolType} onChange={update('highSchoolType')} className="input">
              <option value="">선택 안 함</option>
              {HS_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </Field>
          <p className="text-xs text-slate-400 mt-1">고교 유형에 따른 수시 환경 안내에 쓰입니다. 정시 위치 계산에는 영향 없습니다.</p>
        </div>
      )}

      {isGlobal ? (
        <Field label="목표 대학">
          <select value={form.targetUniversity} onChange={update('targetUniversity')} className="input">
            <option value="">대학을 선택하세요</option>
            {globalUnis.map((s) => (
              <option key={s.id} value={s.universityName}>
                {s.universityName}{s.country ? ` (${s.country})` : ''}
              </option>
            ))}
          </select>
        </Field>
      ) : (
        <div className="relative">
          <Field label="목표 대학">
            <input
              type="text"
              value={form.targetUniversity}
              onChange={(e) => {
                update('targetUniversity')(e);
                setShowList(true);
              }}
              onFocus={() => setShowList(true)}
              placeholder="서울대학교"
              className="input"
              autoComplete="off"
            />
          </Field>
          {showList && suggestions.length > 0 && (
            <ul className="absolute z-10 left-0 right-0 bg-white border border-slate-200 rounded-lg mt-1 max-h-56 overflow-y-auto shadow">
              {suggestions.map((s) => (
                <li
                  key={s.id}
                  onClick={() => pickSuggestion(s)}
                  className="px-3 py-2 hover:bg-slate-100 cursor-pointer text-sm"
                >
                  <span className="font-medium">{s.universityName}</span>
                  {s.departmentName && <span className="text-slate-500"> · {s.departmentName}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {!isGlobal && (
        <Field label="목표 학과">
          <input
            type="text"
            value={form.targetDepartment}
            onChange={update('targetDepartment')}
            placeholder="경영학과"
            className="input"
          />
        </Field>
      )}

      <button
        type="submit"
        disabled={loading}
        className="w-full bg-slate-900 hover:bg-slate-700 disabled:bg-slate-400 text-white py-3 rounded-xl font-semibold transition"
      >
        {loading ? '진단 중...' : '진단하기'}
      </button>

      <style>{`
        .input { width: 100%; border: 1px solid #e2e8f0; border-radius: 0.5rem; padding: 0.5rem 0.75rem; font-size: 0.95rem; }
        .input:focus { outline: 2px solid #0f172a; outline-offset: -1px; }
      `}</style>
    </form>
  );
}

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="block text-sm font-semibold text-slate-700 mb-1">{label}</span>
      {children}
    </label>
  );
}

function GradePicker({ label, value, onChange }) {
  return (
    <label className="block">
      <span className="block text-xs text-slate-500 mb-1">{label}</span>
      <select value={value} onChange={onChange} className="input">
        {GRADES.map((g) => (
          <option key={g} value={g}>{g}등급</option>
        ))}
      </select>
    </label>
  );
}
