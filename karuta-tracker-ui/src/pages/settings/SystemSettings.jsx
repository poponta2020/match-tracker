import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { systemSettingsAPI, organizationAPI } from '../../api';
import { AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../../components/PageHeader';

const SystemSettings = () => {
  const { currentPlayer } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [showConfirm, setShowConfirm] = useState(false);
  const [organizations, setOrganizations] = useState([]);
  const [selectedOrgId, setSelectedOrgId] = useState(null);

  // 設定値
  const [deadlineDays, setDeadlineDays] = useState(0);
  const [noDeadline, setNoDeadline] = useState(false);
  const [capPercentile, setCapPercentile] = useState(30);
  const isSuperAdminUser = currentPlayer?.role === 'SUPER_ADMIN';
  const targetOrgId = isSuperAdminUser ? selectedOrgId : currentPlayer?.adminOrganizationId;
  const requestedOrgId = Number(searchParams.get('organizationId'));
  const latestSettingsRequestIdRef = useRef(0);

  const fetchSettings = useCallback(async (organizationId) => {
    const requestId = ++latestSettingsRequestIdRef.current;
    setLoading(true);
    try {
      const res = await systemSettingsAPI.getAll(organizationId);
      if (latestSettingsRequestIdRef.current !== requestId) return;
      const settings = res.data || [];
      let nextNoDeadline = false;
      let nextDeadlineDays = 0;
      let nextCapPercentile = 30;
      for (const s of settings) {
        if (s.settingKey === 'lottery_deadline_days_before') {
          const val = parseInt(s.settingValue, 10);
          if (val === -1) {
            nextNoDeadline = true;
            nextDeadlineDays = 0;
          } else {
            nextNoDeadline = false;
            nextDeadlineDays = val;
          }
        }
        if (s.settingKey === 'lottery_weight_cap_percentile') {
          // 保存経路にバリデーションが無いため、表示値もバックエンド getter（Integer.parseInt→
          // 失敗時はデフォルト30・その後 0〜100 クランプ）と同じ規則で正規化し、画面表示と実効値を一致させる。
          // parseInt は "45abc" を 45 と受理するため、文字列全体が整数のときだけ採用する（BEの厳密解析に合わせる）。
          const raw = String(s.settingValue);
          const parsed = /^[+-]?\d+$/.test(raw) ? Number(raw) : NaN;
          nextCapPercentile = Number.isNaN(parsed) ? 30 : Math.max(0, Math.min(100, parsed));
        }
      }
      setNoDeadline(nextNoDeadline);
      setDeadlineDays(nextDeadlineDays);
      setCapPercentile(nextCapPercentile);
    } catch {
      if (latestSettingsRequestIdRef.current !== requestId) return;
      setError('設定の取得に失敗しました');
    } finally {
      if (latestSettingsRequestIdRef.current === requestId) {
        setLoading(false);
      }
    }
  }, []);

  // 対象団体
  useEffect(() => {
    if (!currentPlayer) return;

    if (!isSuperAdminUser) {
      setOrganizations([]);
      setSelectedOrgId(currentPlayer?.adminOrganizationId || null);
      return;
    }

    let cancelled = false;
    const fetchOrganizations = async () => {
      try {
        const res = await organizationAPI.getAll();
        if (cancelled) return;
        const orgs = res.data || [];
        setOrganizations(orgs);
        const requestedExists = requestedOrgId > 0 && orgs.some(org => org.id === requestedOrgId);
        const nextOrgId = requestedExists ? requestedOrgId : (orgs[0]?.id || null);
        setSelectedOrgId(nextOrgId);
        if (!nextOrgId) {
          setLoading(false);
        }
      } catch {
        if (!cancelled) {
          setOrganizations([]);
          setSelectedOrgId(null);
          setLoading(false);
          setError('団体の取得に失敗しました');
        }
      }
    };

    fetchOrganizations();
    return () => {
      cancelled = true;
    };
  }, [currentPlayer, isSuperAdminUser, requestedOrgId]);

  useEffect(() => {
    if (!currentPlayer) return;
    if (!targetOrgId) {
      if (!isSuperAdminUser) {
        setLoading(false);
      }
      return;
    }
    fetchSettings(targetOrgId);
  }, [currentPlayer, isSuperAdminUser, targetOrgId, fetchSettings]);

  // 締め切りプレビュー
  const deadlinePreview = useMemo(() => {
    if (noDeadline) return '締め切りなし（いつでも登録変更可能）';
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    const year = nextMonth.getFullYear();
    const month = nextMonth.getMonth() + 1;
    const firstDay = new Date(year, month - 1, 1);
    let deadlineDate;
    if (deadlineDays === 0) {
      deadlineDate = new Date(firstDay);
      deadlineDate.setDate(deadlineDate.getDate() - 1);
    } else {
      deadlineDate = new Date(firstDay);
      deadlineDate.setDate(deadlineDate.getDate() - deadlineDays);
    }
    return `${deadlineDate.getMonth() + 1}月${deadlineDate.getDate()}日`;
  }, [noDeadline, deadlineDays]);

  const handleSave = async () => {
    setShowConfirm(false);
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      if (!targetOrgId) {
        setError('団体を選択してください');
        return;
      }
      const deadlineValue = noDeadline ? '-1' : String(deadlineDays);
      await Promise.all([
        systemSettingsAPI.update('lottery_deadline_days_before', deadlineValue, targetOrgId),
        systemSettingsAPI.update('lottery_weight_cap_percentile', String(capPercentile), targetOrgId),
      ]);
      setSuccess('保存しました');
      setTimeout(() => setSuccess(null), 3000);
    } catch {
      setError('保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const handleDeadlineDaysChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 0 && val <= 20) {
      setDeadlineDays(val);
    }
  };

  const handleCapPercentileChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 0 && val <= 100) {
      setCapPercentile(val);
    }
  };

  const handleOrganizationChange = (e) => {
    const orgId = Number(e.target.value);
    const nextOrgId = orgId > 0 ? orgId : null;
    setSelectedOrgId(nextOrgId);
    setError(null);
    setSuccess(null);
    if (nextOrgId) {
      setSearchParams({ organizationId: String(nextOrgId) });
    }
  };

  if (loading) {
    return (
      <>
        <PageHeader title="システム設定" backTo="/admin/lottery" />
        <LoadingScreen />
      </>
    );
  }

  return (
    <>
      <PageHeader title="システム設定" backTo="/admin/lottery" />
      <div className="max-w-lg mx-auto p-4 space-y-4">
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {success && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600" />
          <p className="text-green-800 text-sm">{success}</p>
        </div>
      )}

      {/* 対象団体 */}
      {isSuperAdminUser && (
        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-2">
          <label htmlFor="system-settings-organization" className="block text-sm font-medium text-gray-700">
            対象団体
          </label>
          <select
            id="system-settings-organization"
            value={selectedOrgId || ''}
            onChange={handleOrganizationChange}
            disabled={saving}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 bg-white disabled:bg-gray-100 disabled:text-gray-400"
          >
            <option value="">団体を選択</option>
            {organizations.map(org => (
              <option key={org.id} value={org.id}>{org.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* 抽選締め切り日数 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">抽選締め切り</h2>
        <p className="text-sm text-gray-500">対象月の初日から何日前を締め切りにするか設定します</p>

        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={noDeadline}
            onChange={(e) => setNoDeadline(e.target.checked)}
            className="w-4 h-4 rounded border-gray-300 text-[#4a6b5a] focus:ring-[#4a6b5a]"
          />
          <span className="text-sm text-gray-700">締め切りなし</span>
        </label>

        <div className="flex items-center gap-2">
          <input
            type="number"
            min="0"
            max="20"
            value={deadlineDays}
            onChange={handleDeadlineDaysChange}
            disabled={noDeadline}
            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center disabled:bg-gray-100 disabled:text-gray-400"
          />
          <span className="text-sm text-gray-700">日前</span>
        </div>

        <div className="bg-gray-50 rounded-lg p-3">
          <p className="text-sm text-gray-600">
            来月の締め切り: <span className="font-medium text-gray-800">{deadlinePreview}</span>
          </p>
        </div>
      </div>

      {/* 重み付けの基準（パーセンタイル） */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">重み付けの基準（パーセンタイル）</h2>
        <p className="text-sm text-gray-500">
          抽選で「直近30日の取得数」に重みをつける際の基準（キャップ）です
        </p>

        <div className="flex items-center gap-2">
          <input
            type="number"
            min="0"
            max="100"
            value={capPercentile}
            onChange={handleCapPercentileChange}
            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center"
          />
          <span className="text-sm text-gray-700">%</span>
        </div>

        <p className="text-sm text-gray-500">
          値を<span className="font-medium text-gray-700">小さくするほど</span>多くの人が横並びになり、よく参加している人も不利になりにくくなります。
          値を<span className="font-medium text-gray-700">大きくするほど</span>参加回数の少ない人がより強く優先されます。
        </p>
      </div>

      {/* 抽選の仕組み */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">抽選の仕組み</h2>

        <div className="space-y-2">
          <p className="text-sm text-gray-700">
            <span className="font-medium">ルール1（その日の一巡保証）:</span>
            定員を超える試合では、<span className="font-medium">その日まだ取れていない人</span>から順に当選します。1日に1試合も取れない人が出ないようにする、確定のルールです。
          </p>
          <p className="text-sm text-gray-700">
            <span className="font-medium">ルール2（直近30日の重み付き抽選）:</span>
            その日の取得数が同じ人どうしでは、<span className="font-medium">直近30日</span>で取れた回数が少ない人ほど当たりやすい重み付き抽選になります。
          </p>
          <p className="text-sm text-gray-700">
            <span className="font-medium">パーセンタイル設定の意味:</span>
            上の「重み付けの基準」は、設定した値（パーセンタイル）を境目にして、それ以上に取れている常連層（設定30なら概ね上位70%）を同じ重みで横並びにする設定です。よく来ている常連層を大きな同着グループにまとめ、特定の人が毎回狙い撃ちで落選するのを防ぎます。
          </p>
        </div>
      </div>

      {/* 保存ボタン */}
      <button
        onClick={() => setShowConfirm(true)}
        disabled={saving}
        className="w-full py-3 bg-[#4a6b5a] text-white rounded-lg font-medium hover:bg-[#3d5a4c] transition-colors disabled:opacity-50"
      >
        {saving ? '保存中...' : '保存'}
      </button>

      {/* 確認ダイアログ */}
      {showConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 mx-4 max-w-sm w-full space-y-4">
            <h3 className="font-semibold text-gray-800">設定を変更しますか？</h3>
            <p className="text-sm text-gray-600">
              変更した設定は即座に反映されます。
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowConfirm(false)}
                className="flex-1 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleSave}
                className="flex-1 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors"
              >
                変更する
              </button>
            </div>
          </div>
        </div>
      )}
      </div>
    </>
  );
};

export default SystemSettings;
