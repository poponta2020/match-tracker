import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { isAdmin } from '../../utils/auth';
import {
  ArrowLeft, RefreshCw, Link2, ChevronLeft, ChevronRight,
  AlertCircle, CheckCircle, UserPlus, Loader2
} from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const DensukeManagement = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const [densukeUrl, setDensukeUrl] = useState('');
  const [savedUrl, setSavedUrl] = useState('');
  const [urlLoading, setUrlLoading] = useState(true);
  const [urlSaving, setUrlSaving] = useState(false);

  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState(null);

  const [registering, setRegistering] = useState(false);
  const [selectedNames, setSelectedNames] = useState([]);

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // 権限チェック
  useEffect(() => {
    if (currentPlayer && !isAdmin()) {
      navigate('/');
    }
  }, [currentPlayer, navigate]);

  // 伝助URL取得
  const fetchUrl = useCallback(async () => {
    setUrlLoading(true);
    setError('');
    setSyncResult(null);
    try {
      const res = await practiceAPI.getDensukeUrl(year, month);
      if (res.status === 200 && res.data) {
        setDensukeUrl(res.data.url || '');
        setSavedUrl(res.data.url || '');
      } else {
        setDensukeUrl('');
        setSavedUrl('');
      }
    } catch {
      setDensukeUrl('');
      setSavedUrl('');
    } finally {
      setUrlLoading(false);
    }
  }, [year, month]);

  useEffect(() => {
    fetchUrl();
  }, [fetchUrl]);

  // 月送り
  const changeMonth = (delta) => {
    let newMonth = month + delta;
    let newYear = year;
    if (newMonth < 1) { newMonth = 12; newYear--; }
    if (newMonth > 12) { newMonth = 1; newYear++; }
    setYear(newYear);
    setMonth(newMonth);
  };

  // URL保存
  const handleSaveUrl = async () => {
    if (!densukeUrl.trim()) {
      setError('URLを入力してください');
      return;
    }
    setUrlSaving(true);
    setError('');
    setSuccess('');
    try {
      await practiceAPI.saveDensukeUrl(year, month, densukeUrl.trim());
      setSavedUrl(densukeUrl.trim());
      setSuccess('URLを保存しました');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      setError(err.response?.data?.message || 'URL保存に失敗しました');
    } finally {
      setUrlSaving(false);
    }
  };

  // 同期実行
  const handleSync = async () => {
    setSyncing(true);
    setError('');
    setSuccess('');
    setSyncResult(null);
    setSelectedNames([]);
    try {
      const res = await practiceAPI.syncDensuke(year, month);
      setSyncResult(res.data);
      if (res.data.registeredCount > 0 || res.data.createdSessionCount > 0) {
        setSuccess(`同期完了: ${res.data.createdSessionCount}日作成、${res.data.registeredCount}名登録`);
      } else {
        setSuccess('同期完了: 変更なし');
      }
    } catch (err) {
      setError(err.response?.data?.message || '同期に失敗しました');
    } finally {
      setSyncing(false);
    }
  };

  // 未登録者一括登録
  const handleRegisterAndSync = async () => {
    if (selectedNames.length === 0) return;
    setRegistering(true);
    setError('');
    setSuccess('');
    try {
      const res = await practiceAPI.registerAndSyncDensuke(selectedNames, year, month);
      setSyncResult(res.data);
      setSelectedNames([]);
      setSuccess(`${selectedNames.length}名を登録し、再同期しました`);
    } catch (err) {
      setError(err.response?.data?.message || '登録に失敗しました');
    } finally {
      setRegistering(false);
    }
  };

  // 未登録者の選択切り替え
  const toggleName = (name) => {
    setSelectedNames(prev =>
      prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name]
    );
  };

  const selectAllUnmatched = () => {
    if (syncResult?.unmatchedNames) {
      setSelectedNames([...syncResult.unmatchedNames]);
    }
  };

  if (!currentPlayer || !isAdmin()) return null;

  return (
    <div className="min-h-screen bg-[#f2ede6] pb-24">
      {/* ヘッダー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
        <div className="max-w-lg mx-auto flex items-center justify-between">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1 text-sm text-white hover:text-white/80"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h1 className="text-lg font-semibold text-white">伝助管理</h1>
          <div className="w-5" />
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 pt-16">
        {/* 月選択 */}
        <div className="mt-4 flex items-center justify-center gap-4">
          <button onClick={() => changeMonth(-1)} className="p-2 rounded-lg hover:bg-[#e5ebe7] transition-colors">
            <ChevronLeft className="w-5 h-5 text-[#374151]" />
          </button>
          <span className="text-lg font-semibold text-[#374151] min-w-[120px] text-center">
            {year}年{month}月
          </span>
          <button onClick={() => changeMonth(1)} className="p-2 rounded-lg hover:bg-[#e5ebe7] transition-colors">
            <ChevronRight className="w-5 h-5 text-[#374151]" />
          </button>
        </div>

        {/* メッセージ表示 */}
        {error && (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="text-sm">{error}</span>
          </div>
        )}
        {success && (
          <div className="mt-4 p-3 bg-green-50 border border-green-200 rounded-lg flex items-center gap-2 text-green-700">
            <CheckCircle className="w-4 h-4 flex-shrink-0" />
            <span className="text-sm">{success}</span>
          </div>
        )}

        {/* URL設定 */}
        <div className="mt-4 bg-[#f9f6f2] rounded-xl p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <Link2 className="w-4 h-4 text-[#4a6b5a]" />
            <h2 className="text-sm font-semibold text-[#374151]">伝助URL</h2>
          </div>

          {urlLoading ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 className="w-5 h-5 animate-spin text-[#4a6b5a]" />
            </div>
          ) : (
            <>
              <input
                type="url"
                value={densukeUrl}
                onChange={(e) => setDensukeUrl(e.target.value)}
                placeholder="https://densuke.biz/..."
                className="w-full px-3 py-2.5 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a]"
              />
              <div className="mt-3 flex gap-2">
                <button
                  onClick={handleSaveUrl}
                  disabled={urlSaving || densukeUrl === savedUrl}
                  className="flex-1 py-2 text-sm bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                >
                  {urlSaving ? '保存中...' : 'URL保存'}
                </button>
                <button
                  onClick={handleSync}
                  disabled={syncing || !savedUrl}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                >
                  {syncing ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <RefreshCw className="w-4 h-4" />
                  )}
                  {syncing ? '同期中...' : '同期実行'}
                </button>
              </div>
            </>
          )}
        </div>

        {/* 同期結果 */}
        {syncResult && (
          <div className="mt-4 bg-[#f9f6f2] rounded-xl p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-[#374151] mb-3">同期結果</h2>

            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="bg-white rounded-lg p-3 text-center">
                <div className="text-2xl font-bold text-[#4a6b5a]">{syncResult.createdSessionCount}</div>
                <div className="text-xs text-[#6b7280]">練習日作成</div>
              </div>
              <div className="bg-white rounded-lg p-3 text-center">
                <div className="text-2xl font-bold text-[#4a6b5a]">{syncResult.registeredCount}</div>
                <div className="text-xs text-[#6b7280]">参加者登録</div>
              </div>
              <div className="bg-white rounded-lg p-3 text-center">
                <div className="text-2xl font-bold text-[#6b7280]">{syncResult.skippedCount}</div>
                <div className="text-xs text-[#6b7280]">スキップ</div>
              </div>
              <div className="bg-white rounded-lg p-3 text-center">
                <div className="text-2xl font-bold text-[#6b7280]">{syncResult.removedCount}</div>
                <div className="text-xs text-[#6b7280]">自動削除</div>
              </div>
            </div>

            {/* 詳細ログ */}
            {syncResult.details?.length > 0 && (
              <div className="mt-3">
                <h3 className="text-xs font-medium text-[#6b7280] mb-1">詳細</h3>
                <div className="bg-white rounded-lg p-3 max-h-40 overflow-y-auto">
                  {syncResult.details.map((detail, i) => (
                    <div key={i} className="text-xs text-[#374151] py-0.5">{detail}</div>
                  ))}
                </div>
              </div>
            )}

            {/* 未登録者一覧 */}
            {syncResult.unmatchedNames?.length > 0 && (
              <div className="mt-4 border-t border-[#e5ebe7] pt-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-semibold text-amber-700">
                    未登録者（{syncResult.unmatchedNames.length}名）
                  </h3>
                  <button
                    onClick={selectAllUnmatched}
                    className="text-xs text-[#4a6b5a] hover:underline"
                  >
                    全選択
                  </button>
                </div>
                <p className="text-xs text-[#6b7280] mb-2">
                  チェックした名前でユーザーを一括登録できます（初期パスワード: pppppppp、ログイン後に変更が必要）
                </p>
                <div className="space-y-1">
                  {syncResult.unmatchedNames.map((name) => (
                    <label
                      key={name}
                      className="flex items-center gap-2 bg-white rounded-lg px-3 py-2 cursor-pointer hover:bg-[#f0ebe3] transition-colors"
                    >
                      <input
                        type="checkbox"
                        checked={selectedNames.includes(name)}
                        onChange={() => toggleName(name)}
                        className="rounded border-[#d4ddd7] text-[#4a6b5a] focus:ring-[#4a6b5a]"
                      />
                      <span className="text-sm text-[#374151]">{name}</span>
                    </label>
                  ))}
                </div>
                <button
                  onClick={handleRegisterAndSync}
                  disabled={registering || selectedNames.length === 0}
                  className="mt-3 w-full flex items-center justify-center gap-1.5 py-2.5 text-sm bg-amber-600 text-white rounded-lg hover:bg-amber-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                >
                  {registering ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <UserPlus className="w-4 h-4" />
                  )}
                  {registering ? '登録中...' : `${selectedNames.length}名を登録して再同期`}
                </button>
              </div>
            )}

            {/* 未マッチ会場 */}
            {syncResult.unmatchedVenues?.length > 0 && (
              <div className="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg">
                <h3 className="text-xs font-medium text-amber-700 mb-1">未登録の会場名</h3>
                <div className="text-xs text-amber-600">
                  {syncResult.unmatchedVenues.join('、')}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default DensukeManagement;
