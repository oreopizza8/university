import { useState } from 'react';

// 시작 시 1회 노출되는 주의사항 모달. '다시 보지 않기'는 localStorage로 기억.
export default function DisclaimerModal({ onClose }) {
  const [dontShow, setDontShow] = useState(false);
  const close = () => {
    if (dontShow) {
      try { localStorage.setItem('disc_dismissed', '1'); } catch (e) { /* private mode */ }
    }
    onClose();
  };
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
        <h2 className="text-lg font-bold text-slate-900">⚠️ 시작 전 꼭 확인하세요</h2>
        <ul className="space-y-3 text-sm text-slate-700 leading-relaxed">
          <li className="flex gap-2">
            <span>📌</span>
            <span>본 도구는 <b>합격을 예측하는 게 아닙니다.</b> 정량 스펙(등급·백분위·내신)으로 내 위치를 빠르게 보는 <b>간단한 자가진단(0단계 스크리닝)</b>입니다.</span>
          </li>
          <li className="flex gap-2">
            <span>📌</span>
            <span>데이터·계산에 <b>오류가 있을 수 있습니다.</b> 최종 판단은 대학 공식자료·전문 컨설팅으로 반드시 확인하세요.</span>
          </li>
          <li className="flex gap-2">
            <span>📌</span>
            <span><b>이화여대·홍익대·한국외대·중앙대 등 일부 상위권</b>은 정시 결과를 백분위로 공개하지 않아 <b>현재 검색에 나오지 않습니다.</b> (자료 미공개 — 누락이 아님)</span>
          </li>
        </ul>
        <label className="flex items-center gap-2 text-xs text-slate-500 cursor-pointer select-none">
          <input type="checkbox" checked={dontShow} onChange={(e) => setDontShow(e.target.checked)} />
          다시 보지 않기
        </label>
        <button onClick={close}
          className="w-full bg-slate-900 text-white font-semibold rounded-lg py-2.5 text-sm hover:bg-slate-700 transition">
          확인했어요
        </button>
      </div>
    </div>
  );
}
