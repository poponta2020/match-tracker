/**
 * 戦績確認画面のタブ帯（カレンダー / 戦績確認）。
 * 緑トップバー直下に固定表示する下線アクティブ式の2タブ。
 * 表示のみを担い、アクティブ状態と切替は親（/matches コンテナ）が握る。
 */
const TABS = [
  { key: 'calendar', label: 'カレンダー' },
  { key: 'record', label: '戦績確認' },
];

const MatchViewTabs = ({ active, onChange }) => (
  <div className="flex bg-white border-b border-[#e5e0da]">
    {TABS.map((t) => {
      const isActive = active === t.key;
      return (
        <button
          key={t.key}
          type="button"
          onClick={() => onChange(t.key)}
          className={`flex-1 py-3 text-sm text-center transition-colors ${
            isActive
              ? 'text-[#4a6b5a] font-bold border-b-2 border-[#4a6b5a]'
              : 'text-gray-500 border-b-2 border-transparent hover:text-[#4a6b5a]'
          }`}
        >
          {t.label}
        </button>
      );
    })}
  </div>
);

export default MatchViewTabs;
