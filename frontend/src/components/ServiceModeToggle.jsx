export default function ServiceModeToggle({ isGlobal, onChange }) {
  return (
    <div className="inline-flex rounded-full bg-white shadow border border-slate-200 p-1">
      <button
        type="button"
        onClick={() => onChange(false)}
        className={`px-5 py-2 rounded-full text-sm font-semibold transition ${
          !isGlobal ? 'bg-slate-900 text-white' : 'text-slate-500'
        }`}
      >
        국내 대입
      </button>
      <button
        type="button"
        onClick={() => onChange(true)}
        className={`px-5 py-2 rounded-full text-sm font-semibold transition ${
          isGlobal ? 'bg-slate-900 text-white' : 'text-slate-500'
        }`}
      >
        해외 유학
      </button>
    </div>
  );
}
