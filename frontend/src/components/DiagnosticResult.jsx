import {
  RadialBarChart,
  RadialBar,
  PolarAngleAxis,
  ResponsiveContainer,
} from 'recharts';
import { SIGNAL_STYLE } from '../constants/signal.js';

export default function DiagnosticResult({ result }) {
  if (!result) return null;

  const noCutoff = result.cutoffAvailable === false;
  const style = noCutoff
    ? { color: '#64748b', bg: 'bg-slate-50', text: 'text-slate-600', border: 'border-slate-300' }
    : SIGNAL_STYLE[result.signal] || SIGNAL_STYLE['적정'];
  const isGlobal = result.mode === 'GLOBAL';
  const isSusi = result.mode === 'SUSI';
  const pct = Math.max(0, Math.min(100, result.nationalPercentile ?? 0));
  const modeLabel = isGlobal ? '해외 유학 모드' : isSusi ? '국내 수시 (학생부교과) 모드' : '국내 정시 모드';

  const data = [{ name: '위치', value: pct, fill: style.color }];

  return (
    <div className="bg-white rounded-2xl shadow p-6 space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <p className="text-xs text-slate-500">{modeLabel}</p>
          <h2 className="text-2xl font-bold text-slate-900">
            {result.targetUniversity}
            {result.targetDepartment && (
              <span className="text-slate-500 text-lg ml-2">· {result.targetDepartment}</span>
            )}
          </h2>
        </div>
        <span
          className={`inline-flex items-center gap-2 px-4 py-2 rounded-full border-2 font-bold ${style.bg} ${style.text} ${style.border}`}
        >
          {noCutoff ? (
            '입결 데이터 없음'
          ) : (
            <>
              ● {result.signal}
              <span className="text-sm font-normal">
                ({result.probabilityMin}% ~ {result.probabilityMax}%)
              </span>
            </>
          )}
        </span>
      </div>

      {noCutoff && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800">
          이 대학·학과의 <b>{isSusi ? '수시 내신' : '정시'} 70%컷 입결 데이터가 아직 없어</b> 합격 가능성 판정은 보류했습니다.
          아래 위치와 일반 조언만 참고하세요. (입결 데이터가 등록되면 합격 판정이 표시됩니다.)
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          <p className="text-sm font-semibold text-slate-700 mb-1">
            {isGlobal ? '목표교 대비 위치' : isSusi ? '내신 상위 위치(근사)' : '전국 백분위'}
          </p>
          <p className="text-xs text-slate-400 mb-1">
            {isGlobal
              ? '목표교 CDS(SAT·GPA) 분포 대비 종합 위치'
              : isSusi
              ? '내신 평균등급의 전국 상위 위치 근사값'
              : `상위 ${Math.max(0, 100 - pct).toFixed(1)}%`}
          </p>
          <div className="h-48">
          <ResponsiveContainer width="100%" height="100%">
            <RadialBarChart
              innerRadius="65%"
              outerRadius="100%"
              data={data}
              startAngle={180}
              endAngle={0}
            >
              <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
              <RadialBar background dataKey="value" cornerRadius={10} />
              <text
                x="50%"
                y="70%"
                textAnchor="middle"
                fontSize="36"
                fontWeight="700"
                fill={style.color}
              >
                {pct.toFixed(1)}%
              </text>
            </RadialBarChart>
          </ResponsiveContainer>
          </div>
        </div>

        <div className="space-y-3">
          <p className="text-sm font-semibold text-slate-700">진단 요약</p>
          <p className="text-slate-700 leading-relaxed">{result.summary}</p>

          {result.mode === 'GLOBAL' && (
            <>
              <div className="grid grid-cols-2 gap-3 pt-2">
                <Stat label="환산 GPA" value={result.convertedGpa?.toFixed(2)} suffix="/4.0" />
                <Stat label="가상 SAT" value={result.convertedSat} suffix="/1600" />
              </div>
              <p className="text-xs text-slate-400">
                ※ 등급 기반 추정치입니다. 유학원별 공식 환산과 다를 수 있어 절대값보다 분포 대비 위치로 보세요.
              </p>
            </>
          )}

          {result.mode === 'DOMESTIC' && result.targetCutoffPercentile != null && (
            <div className="pt-2">
              <Stat label="목표 학과 70% 컷" value={result.targetCutoffPercentile.toFixed(1)} suffix="백분위" />
            </div>
          )}

          {isSusi && result.targetCutoffGrade != null && (
            <div className="pt-2">
              <Stat label={`목표 70% 컷${result.admissionType ? ` (${result.admissionType})` : ''}`}
                    value={result.targetCutoffGrade.toFixed(2)} suffix="내신등급" />
            </div>
          )}
        </div>
      </div>

      {result.dataSource && (
        <p className="text-xs text-slate-400 border-t border-slate-100 pt-3">📑 출처: {result.dataSource}</p>
      )}

      {result.solution && <SolutionReportView solution={result.solution} isGlobal={isGlobal} />}

      {result.highSchoolContext && (
        <div className="border-t border-slate-200 pt-5">
          <p className="text-sm font-semibold text-slate-900 mb-2">
            🏫 수시 환경 안내
            {result.highSchoolType && (
              <span className="ml-2 text-xs font-medium text-slate-500 bg-slate-100 rounded px-2 py-0.5">
                {result.highSchoolType}
              </span>
            )}
          </p>
          <p className="text-sm text-slate-700 leading-relaxed">{result.highSchoolContext}</p>
          <p className="text-xs text-slate-400 mt-2">※ 출신 고교 유형은 수시 전략 참고용입니다. 위 정시 위치(전국 백분위)에는 영향을 주지 않습니다.</p>
        </div>
      )}

      <div className="border-t border-slate-200 pt-5">
        <p className="text-sm font-semibold text-slate-900 mb-1">
          ✅ 상담 가서 이렇게 검증하세요 — 학원·유학원 체크리스트
        </p>
        <p className="text-xs text-slate-500 mb-3">
          위 분석을 들고 가서, 상담사가 같은 근거로 설명하는지 아래 질문으로 확인하세요. 두루뭉술하게 넘어가면 신뢰도를 의심하세요.
        </p>
        <ul className="space-y-2">
          {(result.guideQuestions || []).map((q, i) => (
            <li key={i} className="flex gap-2 text-sm text-slate-700">
              <span className="text-slate-400">{i + 1}.</span>
              <span>{q}</span>
            </li>
          ))}
        </ul>
        <p className="text-xs text-slate-400 mt-4 leading-relaxed">
          ※ 본 서비스는 {isGlobal ? '등급 기반 추정 환산을 적용한' : '계열별 표준 반영비율을 적용한'} <b>전국 단위 위치 스크리닝</b> 결과입니다.
          대학별 고유 반영비율·실시간 경쟁률·학생부 정성평가(에세이·비교과)·수능 최저는 반영되지 않아,
          대형학원 유료 컨설팅 방문 시 미세한 차이가 있을 수 있습니다. 상담 방향(라인)을 사전에 잡는 참고용으로 활용하세요.
        </p>
      </div>
    </div>
  );
}

const ROLE_STYLE = {
  강점: 'text-green-700 bg-green-50 border-green-200',
  취약: 'text-red-700 bg-red-50 border-red-200',
  보통: 'text-slate-600 bg-slate-50 border-slate-200',
};

function SolutionReportView({ solution, isGlobal }) {
  const { rationale, subjects = [], nextSteps = [], strategy = [] } = solution;
  return (
    <div className="border-t border-slate-200 pt-5">
      <p className="text-sm font-semibold text-slate-900 mb-1">📋 상담 전 자가 솔루션 리포트</p>
      <p className="text-xs text-slate-500 mb-4">상담 비용 들이기 전, 지금 내 위치와 다음 수를 스스로 점검하세요.</p>

      <div className="space-y-5">
        {/* 1. 판정 근거 */}
        {rationale && (
          <Block step="1" title="판정 근거">
            <p className="text-sm text-slate-700 leading-relaxed">{rationale}</p>
          </Block>
        )}

        {/* 2. 과목/지표 진단 */}
        {subjects.length > 0 && (
          <Block step="2" title={isGlobal ? '지표 진단' : '취약 · 강점 과목'}>
            <div className="space-y-2">
              {subjects.map((s, i) => (
                <div key={i} className="flex items-center gap-3 text-sm">
                  <span className="w-12 font-medium text-slate-800">{s.subject}</span>
                  <span className={`text-xs font-semibold border rounded px-2 py-0.5 ${ROLE_STYLE[s.role] || ROLE_STYLE['보통']}`}>
                    {s.role}
                  </span>
                  <span className="text-slate-500 text-xs">
                    {s.grade != null && `${s.grade}등급 · `}
                    {s.percentile != null && `위치 ${s.percentile}`}
                    {s.weight != null && ` · 반영 ${Math.round(s.weight * 100)}%`}
                  </span>
                  <span className="text-slate-600 text-xs flex-1">{s.note}</span>
                </div>
              ))}
            </div>
          </Block>
        )}

        {/* 3. 점수 민감도 처방 */}
        {nextSteps.length > 0 && (
          <Block step="3" title="다음 단계 — 무엇을 올리면 판정이 바뀌나">
            <ul className="space-y-1.5">
              {nextSteps.map((t, i) => (
                <li key={i} className="text-sm text-slate-700 leading-relaxed">{t}</li>
              ))}
            </ul>
          </Block>
        )}

        {/* 4. 전형/지원 전략 */}
        {strategy.length > 0 && (
          <Block step="4" title="전형 · 지원 전략">
            <ul className="space-y-1.5">
              {strategy.map((t, i) => (
                <li key={i} className="flex gap-2 text-sm text-slate-700 leading-relaxed">
                  <span className="text-slate-400">▸</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>
          </Block>
        )}
      </div>
    </div>
  );
}

function Block({ step, title, children }) {
  return (
    <div>
      <p className="text-sm font-semibold text-slate-800 mb-2">
        <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-slate-900 text-white text-xs mr-2">
          {step}
        </span>
        {title}
      </p>
      <div className="pl-7">{children}</div>
    </div>
  );
}

function Stat({ label, value, suffix }) {
  return (
    <div className="bg-slate-50 rounded-lg p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="text-xl font-bold text-slate-900">
        {value ?? '—'}
        {suffix && <span className="text-xs text-slate-400 ml-1">{suffix}</span>}
      </p>
    </div>
  );
}
