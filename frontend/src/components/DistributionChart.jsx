import { useEffect, useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
  Cell,
  LabelList,
} from 'recharts';
import { fetchDistribution } from '../api/stats.js';

const MIN_SAMPLE = 50;

export default function DistributionChart({ result }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!result) return;
    let cancelled = false;
    (async () => {
      try {
        const dist = await fetchDistribution({
          mode: result.mode,
          university: result.targetUniversity,
          department: result.targetDepartment,
        });
        if (!cancelled) setData(dist);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [result?.mode, result?.targetUniversity, result?.targetDepartment]);

  if (!result) return null;
  if (error) {
    return (
      <div className="bg-white rounded-2xl shadow p-6 text-sm text-slate-500">
        분포 데이터를 불러오지 못했습니다: {error}
      </div>
    );
  }
  if (!data) {
    return (
      <div className="bg-white rounded-2xl shadow p-6 text-sm text-slate-400 animate-pulse">
        분포 집계 불러오는 중…
      </div>
    );
  }

  const myBucket = Math.max(0, Math.min(95, Math.floor((result.nationalPercentile ?? 0) / 5) * 5));
  const insufficient = data.total < MIN_SAMPLE;

  return (
    <div className="bg-white rounded-2xl shadow p-6 space-y-3">
      <div className="flex items-baseline justify-between flex-wrap gap-2">
        <h3 className="text-sm font-semibold text-slate-900">
          같은 {result.mode === 'GLOBAL' ? '해외' : '국내'} 목표를 본 익명 사용자 분포
        </h3>
        <span className="text-xs text-slate-500">
          표본 {data.total.toLocaleString()}건
          {data.university ? ` · ${data.university}` : ' · 전체'}
          {data.department ? ` · ${data.department}` : ''}
        </span>
      </div>

      {insufficient ? (
        <InsufficientSampleMask total={data.total} threshold={MIN_SAMPLE} myBucket={myBucket} />
      ) : (
        <RealChart data={data} myBucket={myBucket} />
      )}

      <p className="text-xs text-slate-400">
        ※ 표본은 진단 결과만 익명 집계 (개인정보 미보관). 5단위 백분위 양자화로 역추적 방지.
      </p>
    </div>
  );
}

function InsufficientSampleMask({ total, threshold, myBucket }) {
  return (
    <div className="border-2 border-dashed border-slate-300 rounded-lg p-6 bg-slate-50">
      <p className="text-sm text-slate-700 font-medium">분포를 가려 보여드립니다</p>
      <p className="text-xs text-slate-500 mt-1 leading-relaxed">
        현재 표본 <span className="font-semibold text-slate-700">{total}건</span>은 분포를 그릴 때
        노이즈가 신호보다 큽니다 (최소 {threshold}건 필요).
        표본이 쌓이면 자동으로 차트가 나타납니다.
      </p>
      <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
        <div className="bg-white rounded p-2 border border-slate-200">
          <p className="text-slate-500">내 위치</p>
          <p className="font-bold text-slate-900">백분위 {myBucket}–{myBucket + 5}</p>
        </div>
        <div className="bg-white rounded p-2 border border-slate-200">
          <p className="text-slate-500">필요 표본</p>
          <p className="font-bold text-slate-900">{threshold - total}건 더</p>
        </div>
      </div>
    </div>
  );
}

function RealChart({ data, myBucket }) {
  const chartData = data.bins.map((b) => ({
    label: `${b.from}-${b.to}`,
    bucketStart: b.from,
    count: b.count,
    isMe: b.from === myBucket,
  }));

  return (
    <div className="h-56">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={chartData} margin={{ top: 20, right: 10, left: 0, bottom: 0 }}>
          <XAxis
            dataKey="label"
            tick={{ fontSize: 10 }}
            interval={1}
            label={{ value: '백분위 구간', position: 'insideBottom', offset: -5, fontSize: 10, fill: '#94a3b8' }}
          />
          <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
          <Tooltip
            formatter={(v) => [`${v}건`, '응답 수']}
            labelFormatter={(l) => `백분위 ${l}`}
            cursor={{ fill: 'rgba(15, 23, 42, 0.05)' }}
          />
          <Bar dataKey="count" radius={[3, 3, 0, 0]}>
            {chartData.map((d) => (
              <Cell key={d.bucketStart} fill={d.isMe ? '#0f172a' : '#cbd5e1'} />
            ))}
            <LabelList
              dataKey="count"
              position="top"
              fontSize={9}
              fill="#64748b"
              formatter={(v) => (v > 0 ? v : '')}
            />
          </Bar>
          <ReferenceLine
            x={`${myBucket}-${myBucket + 5}`}
            stroke="#dc2626"
            strokeWidth={2}
            strokeDasharray="4 2"
            label={{ value: '나', position: 'top', fill: '#dc2626', fontSize: 12, fontWeight: 700 }}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
