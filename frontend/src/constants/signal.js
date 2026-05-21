// 지원 가능성 신호의 단일 출처 — 백엔드 Signal enum(안정/적정/소신/위험)과 라벨·순서 일치.
// 컴포넌트는 필요한 키(bg/text/dot 또는 color/border)만 골라 쓴다.
export const SIGNAL_STYLE = {
  안정: { color: '#16a34a', bg: 'bg-green-50', text: 'text-green-700', border: 'border-green-300', dot: 'bg-green-500' },
  적정: { color: '#2563eb', bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-300', dot: 'bg-blue-500' },
  소신: { color: '#eab308', bg: 'bg-yellow-50', text: 'text-yellow-700', border: 'border-yellow-300', dot: 'bg-yellow-500' },
  위험: { color: '#dc2626', bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-300', dot: 'bg-red-500' },
};

// 표시 순서: 안정 → 적정 → 소신 → 위험 (객체 삽입 순서 유지)
export const SIGNAL_ORDER = Object.keys(SIGNAL_STYLE);
