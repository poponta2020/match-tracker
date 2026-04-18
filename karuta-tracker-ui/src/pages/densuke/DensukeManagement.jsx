import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI, organizationAPI } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { isAdmin } from '../../utils/auth';
import {
  ArrowLeft, RefreshCw, Link2, ChevronLeft, ChevronRight,
  AlertCircle, CheckCircle, UserPlus, Loader2, Plus, Settings
} from 'lucide-react';
import DensukePageCreateModal from './DensukePageCreateModal';
import DensukeTemplateModal from './DensukeTemplateModal';

const DensukeManagement = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const [organizations, setOrganizations] = useState([]);
  const [orgsLoading, setOrgsLoading] = useState(true);

  // 団体ごとの状態: { [orgId]: { url, savedUrl, urlLoading, urlSaving, syncing, syncResult, writeStatus, registering, selectedNames, error, success } }
  const [orgStates, setOrgStates] = useState({});

  // 権限チェック
  useEffect(() => {
    if (currentPlayer && !isAdmin()) {
      navigate('/');
    }
  }, [currentPlayer, navigate]);

  // 団体一覧取得
  useEffect(() => {
    if (!currentPlayer) return;
    const fetchOrgs = async () => {
      setOrgsLoading(true);
      try {
        const res = await organizationAPI.getAll();
        let orgs = res.data || [];
        if (currentPlayer.role === 'ADMIN') {
          orgs = orgs.filter(o => o.id === currentPlayer.adminOrganizationId);
        }
        setOrganizations(orgs);
      } catch {
        setOrganizations([]);
      } finally {
        setOrgsLoading(false);
      }
    };
    fetchOrgs();
  }, [currentPlayer]);

  // 団体ごとの状態を更新するヘルパー
  const updateOrgState = useCallback((orgId, updates) => {
    setOrgStates(prev => ({
      ...prev,
      [orgId]: { ...prev[orgId], ...updates },
    }));
  }, []);

  // 団体ごとの状態を取得するヘルパー
  const getOrgState = useCallback((orgId) => {
    return orgStates[orgId] || {
      url: '',
      savedUrl: '',
      urlLoading: true,
      urlSaving: false,
      syncing: false,
      syncResult: null,
      writeStatus: null,
      registering: false,
      selectedNames: [],
      error: '',
      success: '',
    };
  }, [orgStates]);

  // 書き込み状況取得（団体ごと）
  const fetchWriteStatus = useCallback(async (orgId) => {
    try {
      const res = await practiceAPI.getDensukeWriteStatus(orgId);
      updateOrgState(orgId, { writeStatus: res.data });
    } catch {
      // エラー時はステータス表示をクリアしない
    }
  }, [updateOrgState]);

  // 伝助URL取得（団体ごと）
  const fetchUrl = useCallback(async (orgId) => {
    updateOrgState(orgId, { urlLoading: true, error: '', syncResult: null });
    try {
      const res = await practiceAPI.getDensukeUrl(year, month, orgId);
      if (res.status === 200 && res.data) {
        updateOrgState(orgId, {
          url: res.data.url || '',
          savedUrl: res.data.url || '',
          urlLoading: false,
        });
      } else {
        updateOrgState(orgId, { url: '', savedUrl: '', urlLoading: false });
      }
    } catch {
      updateOrgState(orgId, { url: '', savedUrl: '', urlLoading: false });
    }
  }, [year, month, updateOrgState]);

  // 団体リストが変わったら各団体のURL・書き込みステータスを取得
  useEffect(() => {
    if (organizations.length === 0) return;
    organizations.forEach(org => {
      fetchUrl(org.id);
      fetchWriteStatus(org.id);
    });
  }, [organizations, fetchUrl, fetchWriteStatus]);

  // 月送り
  const changeMonth = (delta) => {
    let newMonth = month + delta;
    let newYear = year;
    if (newMonth < 1) { newMonth = 12; newYear--; }
    if (newMonth > 12) { newMonth = 1; newYear++; }
    setYear(newYear);
    setMonth(newMonth);
  };

  // URL保存（団体ごと）
  const handleSaveUrl = async (orgId) => {
    const state = getOrgState(orgId);
    if (!state.url.trim()) {
      updateOrgState(orgId, { error: 'URLを入力してください' });
      return;
    }
    updateOrgState(orgId, { urlSaving: true, error: '', success: '' });
    try {
      await practiceAPI.saveDensukeUrl(year, month, state.url.trim(), orgId);
      updateOrgState(orgId, {
        savedUrl: state.url.trim(),
        urlSaving: false,
        success: 'URLを保存しました',
      });
      setTimeout(() => updateOrgState(orgId, { success: '' }), 3000);
    } catch (err) {
      updateOrgState(orgId, {
        urlSaving: false,
        error: err.response?.data?.message || 'URL保存に失敗しました',
      });
    }
  };

  // 同期実行（団体ごと）
  const handleSync = async (orgId) => {
    updateOrgState(orgId, { syncing: true, error: '', success: '', syncResult: null, selectedNames: [] });
    try {
      const res = await practiceAPI.syncDensuke(year, month, orgId);
      updateOrgState(orgId, { syncing: false, syncResult: res.data });
      fetchWriteStatus(orgId);
      if (res.data.registeredCount > 0 || res.data.createdSessionCount > 0) {
        updateOrgState(orgId, {
          success: `同期完了: ${res.data.createdSessionCount}日作成、${res.data.registeredCount}名登録`,
        });
      } else {
        updateOrgState(orgId, { success: '同期完了: 変更なし' });
      }
    } catch (err) {
      updateOrgState(orgId, {
        syncing: false,
        error: err.response?.data?.message || '同期に失敗しました',
      });
    }
  };

  // 未登録者一括登録（団体ごと）
  const handleRegisterAndSync = async (orgId) => {
    const state = getOrgState(orgId);
    if (state.selectedNames.length === 0) return;
    updateOrgState(orgId, { registering: true, error: '', success: '' });
    try {
      const res = await practiceAPI.registerAndSyncDensuke(state.selectedNames, year, month, orgId);
      updateOrgState(orgId, {
        registering: false,
        syncResult: res.data,
        selectedNames: [],
        success: `${state.selectedNames.length}名を登録し、再同期しました`,
      });
    } catch (err) {
      updateOrgState(orgId, {
        registering: false,
        error: err.response?.data?.message || '登録に失敗しました',
      });
    }
  };

  // 未登録者の選択切り替え（団体ごと）
  const toggleName = (orgId, name) => {
    const state = getOrgState(orgId);
    const prev = state.selectedNames || [];
    const next = prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name];
    updateOrgState(orgId, { selectedNames: next });
  };

  const selectAllUnmatched = (orgId) => {
    const state = getOrgState(orgId);
    if (state.syncResult?.unmatchedNames) {
      updateOrgState(orgId, { selectedNames: [...state.syncResult.unmatchedNames] });
    }
  };

  // 作成可能な年月か判定（当月 + 未来2ヶ月まで）
  const monthDiff = (year - now.getFullYear()) * 12 + (month - (now.getMonth() + 1));
  const canCreatePage = monthDiff >= 0 && monthDiff <= 2;

  // モーダル制御
  const openCreateModal = (orgId) => updateOrgState(orgId, { showCreateModal: true });
  const closeCreateModal = (orgId) => updateOrgState(orgId, { showCreateModal: false });
  const handleCreateSuccess = (orgId, result) => {
    updateOrgState(orgId, {
      showCreateModal: false,
      savedUrl: result.url,
      url: result.url,
      success: `伝助ページを作成しました: ${result.url}`,
    });
    fetchWriteStatus(orgId);
    setTimeout(() => updateOrgState(orgId, { success: '' }), 5000);
  };

  const openTemplateModal = (orgId) => updateOrgState(orgId, { showTemplateModal: true });
  const closeTemplateModal = (orgId) => updateOrgState(orgId, { showTemplateModal: false });

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

        {/* 団体読み込み中 */}
        {orgsLoading && (
          <div className="mt-8 flex items-center justify-center py-8">
            <Loader2 className="w-6 h-6 animate-spin text-[#4a6b5a]" />
          </div>
        )}

        {/* 団体ごとのブロック */}
        {!orgsLoading && organizations.map(org => {
          const state = getOrgState(org.id);
          const orgColor = org.color || '#4a6b5a';

          return (
            <div key={org.id} className="mt-6">
              {/* 団体名ヘッダー */}
              <div
                className="flex items-center gap-2 px-4 py-2.5 rounded-t-xl"
                style={{ backgroundColor: orgColor }}
              >
                <h2 className="text-sm font-semibold text-white">{org.name}</h2>
              </div>

              <div className="bg-[#f9f6f2] rounded-b-xl shadow-sm overflow-hidden">
                {/* メッセージ表示 */}
                {state.error && (
                  <div className="mx-4 mt-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                    <span className="text-sm">{state.error}</span>
                  </div>
                )}
                {state.success && (
                  <div className="mx-4 mt-4 p-3 bg-green-50 border border-green-200 rounded-lg flex items-center gap-2 text-green-700">
                    <CheckCircle className="w-4 h-4 flex-shrink-0" />
                    <span className="text-sm">{state.success}</span>
                  </div>
                )}

                {/* URL設定 */}
                <div className="p-5">
                  <div className="flex items-center gap-2 mb-3">
                    <Link2 className="w-4 h-4" style={{ color: orgColor }} />
                    <h3 className="text-sm font-semibold text-[#374151]">伝助URL</h3>
                  </div>

                  {state.urlLoading ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="w-5 h-5 animate-spin" style={{ color: orgColor }} />
                    </div>
                  ) : (
                    <>
                      <input
                        type="url"
                        value={state.url}
                        onChange={(e) => updateOrgState(org.id, { url: e.target.value })}
                        placeholder="https://densuke.biz/..."
                        className="w-full px-3 py-2.5 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a]"
                      />
                      <div className="mt-3 flex gap-2">
                        <button
                          onClick={() => handleSaveUrl(org.id)}
                          disabled={state.urlSaving || state.url === state.savedUrl}
                          className="flex-1 py-2 text-sm text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                          style={{ backgroundColor: orgColor }}
                          onMouseEnter={(e) => { if (!e.currentTarget.disabled) e.currentTarget.style.opacity = '0.85'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.opacity = '1'; }}
                        >
                          {state.urlSaving ? '保存中...' : 'URL保存'}
                        </button>
                        <button
                          onClick={() => handleSync(org.id)}
                          disabled={state.syncing || !state.savedUrl}
                          className="flex-1 flex items-center justify-center gap-1.5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                        >
                          {state.syncing ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                          ) : (
                            <RefreshCw className="w-4 h-4" />
                          )}
                          {state.syncing ? '同期中...' : '同期実行'}
                        </button>
                      </div>

                      {/* 伝助ページ作成 + テンプレート編集 */}
                      <div className="mt-2 flex gap-2">
                        {canCreatePage && !state.savedUrl && (
                          <button
                            onClick={() => openCreateModal(org.id)}
                            className="flex-1 flex items-center justify-center gap-1.5 py-2 text-sm text-white rounded-lg font-medium"
                            style={{ backgroundColor: orgColor }}
                          >
                            <Plus className="w-4 h-4" />
                            伝助ページ作成
                          </button>
                        )}
                        <button
                          onClick={() => openTemplateModal(org.id)}
                          className={`${canCreatePage && !state.savedUrl ? 'flex-1' : 'w-full'} flex items-center justify-center gap-1.5 py-2 text-sm border border-[#d4ddd7] rounded-lg font-medium text-[#374151] hover:bg-[#f9f6f2]`}
                        >
                          <Settings className="w-4 h-4" />
                          テンプレート編集
                        </button>
                      </div>
                    </>
                  )}
                </div>

                {/* 伝助への書き込み状況 */}
                {state.writeStatus && (
                  <div className="mx-5 mb-5 bg-white rounded-lg p-4">
                    <h3 className="text-sm font-semibold text-[#374151] mb-3">伝助への書き込み状況</h3>
                    <div className="space-y-2 text-sm">
                      <div className="flex justify-between">
                        <span className="text-[#6b7280]">書き込み待ち</span>
                        <span className={`font-medium ${state.writeStatus.pendingCount > 0 ? 'text-amber-600' : 'text-[#374151]'}`}>
                          {state.writeStatus.pendingCount}件
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#6b7280]">最終試行</span>
                        <span className="text-[#374151]">
                          {state.writeStatus.lastAttemptAt
                            ? new Date(state.writeStatus.lastAttemptAt).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })
                            : '未実行'}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#6b7280]">最終成功</span>
                        <span className="text-[#374151]">
                          {state.writeStatus.lastSuccessAt
                            ? new Date(state.writeStatus.lastSuccessAt).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })
                            : 'なし'}
                        </span>
                      </div>
                    </div>
                    {state.writeStatus.errors?.length > 0 && (
                      <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg">
                        <h4 className="text-xs font-medium text-red-700 mb-1">エラー</h4>
                        <div className="space-y-0.5 max-h-32 overflow-y-auto">
                          {state.writeStatus.errors.map((err, i) => (
                            <div key={i} className="text-xs text-red-600">{err}</div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* 同期結果 */}
                {state.syncResult && (
                  <div className="mx-5 mb-5 bg-white rounded-lg p-4">
                    <h3 className="text-sm font-semibold text-[#374151] mb-3">同期結果</h3>

                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="bg-[#f9f6f2] rounded-lg p-3 text-center">
                        <div className="text-2xl font-bold" style={{ color: orgColor }}>{state.syncResult.createdSessionCount}</div>
                        <div className="text-xs text-[#6b7280]">練習日作成</div>
                      </div>
                      <div className="bg-[#f9f6f2] rounded-lg p-3 text-center">
                        <div className="text-2xl font-bold" style={{ color: orgColor }}>{state.syncResult.registeredCount}</div>
                        <div className="text-xs text-[#6b7280]">参加者登録</div>
                      </div>
                      <div className="bg-[#f9f6f2] rounded-lg p-3 text-center">
                        <div className="text-2xl font-bold text-[#6b7280]">{state.syncResult.skippedCount}</div>
                        <div className="text-xs text-[#6b7280]">スキップ</div>
                      </div>
                      <div className="bg-[#f9f6f2] rounded-lg p-3 text-center">
                        <div className="text-2xl font-bold text-[#6b7280]">{state.syncResult.removedCount}</div>
                        <div className="text-xs text-[#6b7280]">自動削除</div>
                      </div>
                    </div>

                    {/* 詳細ログ */}
                    {state.syncResult.details?.length > 0 && (
                      <div className="mt-3">
                        <h4 className="text-xs font-medium text-[#6b7280] mb-1">詳細</h4>
                        <div className="bg-[#f9f6f2] rounded-lg p-3 max-h-40 overflow-y-auto">
                          {state.syncResult.details.map((detail, i) => (
                            <div key={i} className="text-xs text-[#374151] py-0.5">{detail}</div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* 未登録者一覧 */}
                    {state.syncResult.unmatchedNames?.length > 0 && (
                      <div className="mt-4 border-t border-[#e5ebe7] pt-4">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-sm font-semibold text-amber-700">
                            未登録者（{state.syncResult.unmatchedNames.length}名）
                          </h4>
                          <button
                            onClick={() => selectAllUnmatched(org.id)}
                            className="text-xs hover:underline"
                            style={{ color: orgColor }}
                          >
                            全選択
                          </button>
                        </div>
                        <p className="text-xs text-[#6b7280] mb-2">
                          チェックした名前でユーザーを一括登録できます（初期パスワード: pppppppp、ログイン後に変更が必要）
                        </p>
                        <div className="space-y-1">
                          {state.syncResult.unmatchedNames.map((name) => (
                            <label
                              key={name}
                              className="flex items-center gap-2 bg-[#f9f6f2] rounded-lg px-3 py-2 cursor-pointer hover:bg-[#f0ebe3] transition-colors"
                            >
                              <input
                                type="checkbox"
                                checked={(state.selectedNames || []).includes(name)}
                                onChange={() => toggleName(org.id, name)}
                                className="rounded border-[#d4ddd7] text-[#4a6b5a] focus:ring-[#4a6b5a]"
                              />
                              <span className="text-sm text-[#374151]">{name}</span>
                            </label>
                          ))}
                        </div>
                        <button
                          onClick={() => handleRegisterAndSync(org.id)}
                          disabled={state.registering || (state.selectedNames || []).length === 0}
                          className="mt-3 w-full flex items-center justify-center gap-1.5 py-2.5 text-sm bg-amber-600 text-white rounded-lg hover:bg-amber-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                        >
                          {state.registering ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                          ) : (
                            <UserPlus className="w-4 h-4" />
                          )}
                          {state.registering ? '登録中...' : `${(state.selectedNames || []).length}名を登録して再同期`}
                        </button>
                      </div>
                    )}

                    {/* 未マッチ会場 */}
                    {state.syncResult.unmatchedVenues?.length > 0 && (
                      <div className="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg">
                        <h4 className="text-xs font-medium text-amber-700 mb-1">未登録の会場名</h4>
                        <div className="text-xs text-amber-600">
                          {state.syncResult.unmatchedVenues.join('、')}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* モーダル */}
              <DensukePageCreateModal
                isOpen={!!state.showCreateModal}
                onClose={() => closeCreateModal(org.id)}
                year={year}
                month={month}
                organizationId={org.id}
                orgName={org.name}
                orgColor={orgColor}
                onSuccess={(result) => handleCreateSuccess(org.id, result)}
              />
              <DensukeTemplateModal
                isOpen={!!state.showTemplateModal}
                onClose={() => closeTemplateModal(org.id)}
                organizationId={org.id}
                orgName={org.name}
                orgColor={orgColor}
              />
            </div>
          );
        })}

        {/* 団体が0件の場合 */}
        {!orgsLoading && organizations.length === 0 && (
          <div className="mt-8 text-center text-sm text-[#6b7280]">
            管理対象の団体がありません
          </div>
        )}
      </div>
    </div>
  );
};

export default DensukeManagement;
