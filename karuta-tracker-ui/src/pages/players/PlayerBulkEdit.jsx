import { useState, useEffect, useMemo } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { organizationAPI } from '../../api/organizations';
import { Save, X, AlertTriangle } from 'lucide-react';
import PageHeader from '../../components/PageHeader';
import { KYU_RANKS, DAN_RANKS, defaultDanForKyu } from '../../utils/rank';

const GENDERS = ['男性', '女性', 'その他'];

const PlayerBulkEdit = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const initialPlayers = location.state?.players;

  const [organizations, setOrganizations] = useState([]);
  const [rows, setRows] = useState(() =>
    (initialPlayers || []).map((p) => ({
      id: p.id,
      name: p.name,
      gender: p.gender || '',
      kyuRank: p.kyuRank || '',
      danRank: p.danRank || '',
      karutaClub: p.karutaClub || '',
      organizationIds: p.organizationIds || [],
      addOrgIds: [],
    }))
  );

  // 全員一括設定コントロール
  const [bulkGender, setBulkGender] = useState('');
  const [bulkKyu, setBulkKyu] = useState('');
  const [bulkKarutaClub, setBulkKarutaClub] = useState('');

  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    organizationAPI.getAll()
      .then((res) => setOrganizations(res.data))
      .catch((err) => console.error('Failed to fetch organizations:', err));
  }, []);

  const orgById = useMemo(() => {
    const m = {};
    organizations.forEach((o) => { m[o.id] = o; });
    return m;
  }, [organizations]);

  const hokudai = useMemo(() => organizations.find((o) => o.code === 'hokudai'), [organizations]);
  const wasura = useMemo(() => organizations.find((o) => o.code === 'wasura'), [organizations]);

  // 追加予定も含めた所属団体ID -> 何人に追加するかの集計（確認ダイアログ用）
  const orgAddSummary = useMemo(() => {
    const counts = {};
    rows.forEach((r) => r.addOrgIds.forEach((oid) => { counts[oid] = (counts[oid] || 0) + 1; }));
    return Object.entries(counts).map(([oid, count]) => ({
      name: orgById[Number(oid)]?.name || `団体#${oid}`,
      count,
    }));
  }, [rows, orgById]);

  // state なしアクセス（直接URL/リロード）は一覧へリダイレクト
  if (!initialPlayers || initialPlayers.length === 0) {
    return <Navigate to="/players" replace />;
  }

  const setRow = (id, patch) => {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  };

  const handleRowKyuChange = (id, kyuRank) => {
    setRow(id, { kyuRank, danRank: defaultDanForKyu(kyuRank) });
  };

  // ===== 全員一括設定 =====
  const applyBulkGender = () => {
    if (!bulkGender) return;
    setRows((prev) => prev.map((r) => ({ ...r, gender: bulkGender })));
  };

  const applyBulkKyu = (kyu) => {
    if (!kyu) return;
    setRows((prev) => prev.map((r) => ({ ...r, kyuRank: kyu, danRank: defaultDanForKyu(kyu) })));
  };

  const applyBulkKarutaClub = () => {
    if (!bulkKarutaClub.trim()) return;
    setRows((prev) => prev.map((r) => ({ ...r, karutaClub: bulkKarutaClub })));
  };

  const rowHasOrg = (row, orgId) =>
    (row.organizationIds || []).includes(orgId) || row.addOrgIds.includes(orgId);

  const bulkAddOrg = (org) => {
    if (!org) return;
    setRows((prev) => prev.map((r) => (rowHasOrg(r, org.id) ? r : { ...r, addOrgIds: [...r.addOrgIds, org.id] })));
  };

  const addOrgToRow = (id, org) => {
    if (!org) return;
    setRows((prev) => prev.map((r) => {
      if (r.id !== id) return r;
      return rowHasOrg(r, org.id) ? r : { ...r, addOrgIds: [...r.addOrgIds, org.id] };
    }));
  };

  const removeAddOrgFromRow = (id, orgId) => {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, addOrgIds: r.addOrgIds.filter((x) => x !== orgId) } : r)));
  };

  // ===== 保存 =====
  const buildUpdates = () => rows.map((r) => ({
    playerId: r.id,
    gender: r.gender || null,
    kyuRank: r.kyuRank || null,
    danRank: r.danRank || null,
    karutaClub: r.karutaClub || null,
    addOrganizationIds: r.addOrgIds,
  }));

  const handleSave = async () => {
    setError('');
    setSubmitting(true);
    try {
      await playerAPI.bulkUpdate(buildUpdates());
      navigate('/players');
    } catch (err) {
      console.error('Failed to bulk update players:', err);
      const msg = err.response?.data?.message || err.response?.data?.error || '';
      setError(`一括更新に失敗しました${msg ? `: ${msg}` : ''}`);
      setShowConfirm(false);
      setSubmitting(false);
    }
  };

  // 現在＋追加予定の所属団体に北大/わすらが含まれているか（追加ボタンの活性制御）
  const renderOrgAddButtons = (row) => (
    <div className="flex flex-wrap gap-2">
      <button
        type="button"
        onClick={() => addOrgToRow(row.id, hokudai)}
        disabled={!hokudai || rowHasOrg(row, hokudai?.id)}
        className="px-2.5 py-1 text-xs rounded-full border border-[#4a6b5a] text-[#4a6b5a] hover:bg-[#f0f4f1] disabled:opacity-40 disabled:cursor-not-allowed"
      >
        ＋北大
      </button>
      <button
        type="button"
        onClick={() => addOrgToRow(row.id, wasura)}
        disabled={!wasura || rowHasOrg(row, wasura?.id)}
        className="px-2.5 py-1 text-xs rounded-full border border-[#4a6b5a] text-[#4a6b5a] hover:bg-[#f0f4f1] disabled:opacity-40 disabled:cursor-not-allowed"
      >
        ＋わすら
      </button>
    </div>
  );

  return (
    <>
      <PageHeader title="選手一括編集" backTo="/players" />

      <div className="space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 p-3 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {/* 全員一括設定エリア */}
        <div className="bg-white shadow-sm rounded-lg p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-900">全員に一括設定</h2>

          {/* 性別 */}
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-600 mb-1">性別</label>
              <select
                aria-label="一括設定する性別"
                value={bulkGender}
                onChange={(e) => setBulkGender(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                <option value="">選択...</option>
                {GENDERS.map((g) => <option key={g} value={g}>{g}</option>)}
              </select>
            </div>
            <button
              type="button"
              onClick={applyBulkGender}
              disabled={!bulkGender}
              className="px-3 py-2 text-sm bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] disabled:opacity-50"
            >
              全員に適用
            </button>
          </div>

          {/* 級 */}
          <div className="flex items-end gap-2 flex-wrap">
            <div className="flex-1 min-w-[120px]">
              <label className="block text-xs text-gray-600 mb-1">級</label>
              <select
                aria-label="一括設定する級"
                value={bulkKyu}
                onChange={(e) => setBulkKyu(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                <option value="">選択...</option>
                {KYU_RANKS.map((k) => <option key={k} value={k}>{k}</option>)}
              </select>
            </div>
            <button
              type="button"
              onClick={() => applyBulkKyu(bulkKyu)}
              disabled={!bulkKyu}
              className="px-3 py-2 text-sm bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] disabled:opacity-50"
            >
              全員に適用
            </button>
            <button
              type="button"
              onClick={() => applyBulkKyu('E級')}
              className="px-3 py-2 text-sm bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c]"
            >
              全員をE級に
            </button>
          </div>

          {/* 所属かるた会 */}
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-600 mb-1">所属かるた会</label>
              <input
                type="text"
                aria-label="一括設定する所属かるた会"
                value={bulkKarutaClub}
                onChange={(e) => setBulkKarutaClub(e.target.value)}
                maxLength={200}
                placeholder="〇〇かるた会"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              />
            </div>
            <button
              type="button"
              onClick={applyBulkKarutaClub}
              disabled={!bulkKarutaClub.trim()}
              className="px-3 py-2 text-sm bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] disabled:opacity-50"
            >
              全員に適用
            </button>
          </div>

          {/* 所属練習会（追加のみ） */}
          <div>
            <label className="block text-xs text-gray-600 mb-1">所属練習会（追加のみ）</label>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => bulkAddOrg(hokudai)}
                disabled={!hokudai}
                className="px-3 py-2 text-sm bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] disabled:opacity-50"
              >
                全員に北大を追加
              </button>
              <button
                type="button"
                onClick={() => bulkAddOrg(wasura)}
                disabled={!wasura}
                className="px-3 py-2 text-sm bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] disabled:opacity-50"
              >
                全員にわすらを追加
              </button>
            </div>
          </div>
        </div>

        {/* 選手ごとの編集 */}
        <p className="text-xs text-gray-500 px-1">
          ※ 値の設定・所属の追加のみ反映されます。級・段位・所属かるた会を空に戻す操作は保存されません（単体編集と同一）。
        </p>
        <div className="space-y-3">
          {rows.map((row) => (
            <div key={row.id} className="bg-white shadow-sm rounded-lg p-4">
              <div className="font-semibold text-gray-900 mb-3">{row.name}</div>

              <div className="grid grid-cols-2 gap-3">
                {/* 性別 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">性別</label>
                  <select
                    aria-label={`${row.name}の性別`}
                    value={row.gender}
                    onChange={(e) => setRow(row.id, { gender: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  >
                    {/* 性別は必須のため空（未設定）の選択肢は設けない */}
                    {GENDERS.map((g) => <option key={g} value={g}>{g}</option>)}
                  </select>
                </div>

                {/* 級 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">級</label>
                  <select
                    aria-label={`${row.name}の級`}
                    value={row.kyuRank}
                    onChange={(e) => handleRowKyuChange(row.id, e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  >
                    {/* 空（初心者）は現状表示用。一度設定したら空には戻せない（クリア非対応） */}
                    <option value="" disabled>初心者</option>
                    {KYU_RANKS.map((k) => <option key={k} value={k}>{k}</option>)}
                  </select>
                </div>

                {/* 段位（A級のみ手動指定可） */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">
                    段位{row.kyuRank === 'A級' && ' (四段〜八段)'}
                  </label>
                  <select
                    aria-label={`${row.name}の段位`}
                    value={row.danRank}
                    onChange={(e) => setRow(row.id, { danRank: e.target.value })}
                    disabled={row.kyuRank !== 'A級'}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm disabled:bg-gray-100 disabled:cursor-not-allowed"
                  >
                    <option value="" disabled>未設定</option>
                    {DAN_RANKS.map((d) => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>

                {/* 所属かるた会 */}
                <div>
                  <label className="block text-xs text-gray-600 mb-1">所属かるた会</label>
                  <input
                    type="text"
                    aria-label={`${row.name}の所属かるた会`}
                    value={row.karutaClub}
                    onChange={(e) => setRow(row.id, { karutaClub: e.target.value })}
                    maxLength={200}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  />
                </div>
              </div>

              {/* 所属練習会 */}
              <div className="mt-3">
                <label className="block text-xs text-gray-600 mb-1">所属練習会</label>
                <div className="flex flex-wrap items-center gap-1.5 mb-2">
                  {(row.organizationIds || []).length === 0 && row.addOrgIds.length === 0 && (
                    <span className="text-xs text-gray-400">なし</span>
                  )}
                  {/* 既存の所属（削除不可） */}
                  {(row.organizationIds || []).map((oid) => (
                    <span
                      key={`cur-${oid}`}
                      className="px-2 py-0.5 text-xs rounded-full text-white"
                      style={{ backgroundColor: orgById[oid]?.color || '#6b7280' }}
                    >
                      {orgById[oid]?.name || `団体#${oid}`}
                    </span>
                  ))}
                  {/* 追加予定（取り消し可） */}
                  {row.addOrgIds.map((oid) => (
                    <span
                      key={`add-${oid}`}
                      className="px-2 py-0.5 text-xs rounded-full border border-dashed border-[#4a6b5a] text-[#4a6b5a] flex items-center gap-1"
                    >
                      {orgById[oid]?.name || `団体#${oid}`}（追加予定）
                      <button
                        type="button"
                        onClick={() => removeAddOrgFromRow(row.id, oid)}
                        aria-label={`${orgById[oid]?.name || '団体'}の追加を取り消し`}
                        className="hover:text-red-600"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                </div>
                {renderOrgAddButtons(row)}
              </div>
            </div>
          ))}
        </div>

        {/* 操作ボタン */}
        <div className="flex gap-3 pt-2">
          <button
            type="button"
            onClick={() => setShowConfirm(true)}
            className="flex items-center gap-2 bg-[#4a6b5a] text-white px-6 py-3 rounded-lg hover:bg-[#3d5a4c] transition-colors"
          >
            <Save className="w-5 h-5" />
            保存
          </button>
          <button
            type="button"
            onClick={() => navigate('/players')}
            className="px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            キャンセル
          </button>
        </div>
      </div>

      {/* 確認ダイアログ */}
      {showConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4">
          <div className="bg-white rounded-xl shadow-lg max-w-md w-full p-5">
            <h3 className="text-base font-semibold text-gray-900 mb-2">一括更新の確認</h3>
            <p className="text-sm text-gray-700 mb-3">
              {rows.length}人に以下の変更を適用します。
            </p>
            <ul className="text-sm text-gray-700 list-disc pl-5 space-y-1 mb-3">
              <li>性別・級・段位・所属かるた会：各選手の編集内容で上書き</li>
              {orgAddSummary.length > 0 ? (
                orgAddSummary.map((s) => (
                  <li key={s.name}>所属練習会「{s.name}」を{s.count}人に追加</li>
                ))
              ) : (
                <li>所属練習会の追加：なし</li>
              )}
            </ul>
            <div className="flex items-start gap-2 bg-yellow-50 border border-yellow-200 rounded-lg p-2.5 mb-4">
              <AlertTriangle className="w-4 h-4 text-yellow-600 flex-shrink-0 mt-0.5" />
              <p className="text-xs text-yellow-800">
                所属練習会は追加のみで、既存の所属は削除されません。
              </p>
            </div>
            {error && (
              <div className="bg-red-50 border border-red-200 p-2.5 rounded-lg text-red-700 text-sm mb-3">
                {error}
              </div>
            )}
            <div className="flex gap-3 justify-end">
              <button
                type="button"
                onClick={() => setShowConfirm(false)}
                disabled={submitting}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 text-sm disabled:opacity-50"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={submitting}
                className="px-4 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] text-sm disabled:opacity-50"
              >
                {submitting ? '適用中...' : '適用する'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default PlayerBulkEdit;
