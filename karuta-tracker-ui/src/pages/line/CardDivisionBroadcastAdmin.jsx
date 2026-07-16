import { useState, useEffect, useCallback, Fragment } from 'react';
import { lineBroadcastAPI } from '../../api';
import { Plus, AlertCircle, Trash2, Star, Info } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';

const STATUS_LABELS = {
  RESERVED: '予約済み',
  SUCCESS: '成功',
  FAILED: '失敗',
  SKIPPED: 'スキップ',
};

const STATUS_STYLES = {
  RESERVED: 'bg-blue-100 text-blue-800',
  SUCCESS: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  SKIPPED: 'bg-yellow-100 text-yellow-800',
};

const CardDivisionBroadcastAdmin = () => {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [showForm, setShowForm] = useState(false);
  const [createForm, setCreateForm] = useState({ organizationId: '', name: '', expectedRecipientCount: '' });

  const [editingGroupId, setEditingGroupId] = useState(null);
  const [editForm, setEditForm] = useState({ name: '', expectedRecipientCount: '' });

  const [selectedGroupId, setSelectedGroupId] = useState(null);
  const [status, setStatus] = useState(null);
  const [logs, setLogs] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);
  const [assignChannelId, setAssignChannelId] = useState('');

  const fetchGroups = useCallback(async () => {
    try {
      setLoading(true);
      const res = await lineBroadcastAPI.getGroups();
      setGroups(res.data);
    } catch {
      setError('配信グループ一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  const fetchDetail = useCallback(async (groupId) => {
    try {
      setDetailLoading(true);
      setDetailError(null);
      const [statusRes, logsRes] = await Promise.all([
        lineBroadcastAPI.getStatus(groupId),
        lineBroadcastAPI.getLogs(groupId),
      ]);
      setStatus(statusRes.data);
      setLogs(logsRes.data);
    } catch {
      setDetailError('稼働状況・配信ログの取得に失敗しました');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedGroupId) {
      fetchDetail(selectedGroupId);
    }
  }, [selectedGroupId, fetchDetail]);

  const handleSelectGroup = (groupId) => {
    setSelectedGroupId(groupId === selectedGroupId ? null : groupId);
    setStatus(null);
    setLogs(null);
    setDetailError(null);
    setAssignChannelId('');
  };

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    try {
      const body = {
        organizationId: Number(createForm.organizationId),
        name: createForm.name,
      };
      if (createForm.expectedRecipientCount !== '') {
        body.expectedRecipientCount = Number(createForm.expectedRecipientCount);
      }
      await lineBroadcastAPI.createGroup(body);
      setShowForm(false);
      setCreateForm({ organizationId: '', name: '', expectedRecipientCount: '' });
      await fetchGroups();
    } catch {
      setError('配信グループの登録に失敗しました');
    }
  };

  const handleToggleEnabled = async (group) => {
    try {
      await lineBroadcastAPI.updateGroup(group.id, { enabled: !group.enabled });
      await fetchGroups();
      if (selectedGroupId === group.id) {
        await fetchDetail(group.id);
      }
    } catch {
      setError('配信グループの更新に失敗しました');
    }
  };

  const startEdit = (group) => {
    setEditingGroupId(group.id);
    setEditForm({
      name: group.name,
      expectedRecipientCount: group.expectedRecipientCount ?? '',
    });
  };

  const cancelEdit = () => {
    setEditingGroupId(null);
    setEditForm({ name: '', expectedRecipientCount: '' });
  };

  const handleSaveEdit = async (groupId) => {
    try {
      await lineBroadcastAPI.updateGroup(groupId, {
        name: editForm.name,
        expectedRecipientCount:
          editForm.expectedRecipientCount === '' ? null : Number(editForm.expectedRecipientCount),
      });
      setEditingGroupId(null);
      await fetchGroups();
      if (selectedGroupId === groupId) {
        await fetchDetail(groupId);
      }
    } catch {
      setError('配信グループの更新に失敗しました');
    }
  };

  const handleAssignBot = async (e, groupId) => {
    e.preventDefault();
    if (!assignChannelId) return;
    try {
      setDetailError(null);
      await lineBroadcastAPI.assignBot(groupId, Number(assignChannelId));
      setAssignChannelId('');
      await fetchDetail(groupId);
      await fetchGroups();
    } catch {
      setDetailError('botの割当に失敗しました');
    }
  };

  const handleUnassignBot = async (groupId, channelId) => {
    if (!confirm('このbotの割当を解除しますか？')) return;
    try {
      setDetailError(null);
      await lineBroadcastAPI.unassignBot(groupId, channelId);
      await fetchDetail(groupId);
      await fetchGroups();
    } catch {
      setDetailError('botの割当解除に失敗しました');
    }
  };

  if (loading) {
    return (
      <>
        <PageHeader title="全体LINE配信管理" backTo="/settings" />
        <LoadingScreen />
      </>
    );
  }

  return (
    <>
      <PageHeader title="全体LINE配信管理" backTo="/settings" />
      <div className="max-w-4xl mx-auto p-4 space-y-4">
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-start gap-2">
          <Info className="h-5 w-5 text-blue-600 mt-0.5 flex-shrink-0" />
          <div className="text-blue-900 text-sm space-y-1">
            <p className="font-semibold">bot群セットアップ手順</p>
            <p>① 各botのLINEチャネル設定で「グループトーク参加を許可」を有効化し、Webhookも有効化する（既存の「Webhook URL一括移行」機能を利用可）。</p>
            <p>② 全体グループに対象botを人手で招待すると、join Webhookでグループ ID が自動捕捉される（「未参加」バッジが外れる）。</p>
          </div>
        </div>

        <div className="flex items-center justify-end">
          <button
            onClick={() => setShowForm(!showForm)}
            className="flex items-center gap-1 bg-[#4a6b5a] text-white py-2 px-3 rounded-lg text-sm hover:bg-[#3d5a4c] transition-colors"
          >
            <Plus className="h-4 w-4" />
            配信グループ登録
          </button>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="h-5 w-5 text-red-600" />
            <p className="text-red-800 text-sm">{error}</p>
          </div>
        )}

        {showForm && (
          <form onSubmit={handleCreateGroup} className="bg-white border rounded-lg p-4 space-y-3">
            <h2 className="font-semibold text-gray-700">配信グループ登録</h2>
            <div>
              <label className="block text-sm text-gray-600 mb-1">団体ID *</label>
              <input
                type="number"
                value={createForm.organizationId}
                onChange={(e) => setCreateForm({ ...createForm, organizationId: e.target.value })}
                required
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">名称 *</label>
              <input
                type="text"
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                required
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">想定受信数（任意）</label>
              <input
                type="number"
                value={createForm.expectedRecipientCount}
                onChange={(e) => setCreateForm({ ...createForm, expectedRecipientCount: e.target.value })}
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="bg-[#4a6b5a] text-white py-2 px-4 rounded-lg text-sm hover:bg-[#3d5a4c]">
                登録
              </button>
              <button type="button" onClick={() => setShowForm(false)} className="bg-gray-100 py-2 px-4 rounded-lg text-sm hover:bg-gray-200">
                キャンセル
              </button>
            </div>
          </form>
        )}

        {/* 配信グループ一覧 */}
        <div className="bg-white rounded-lg border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">名称</th>
                <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">団体</th>
                <th className="text-center px-2 py-2 text-gray-600 whitespace-nowrap">有効</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">想定受信数</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">割当bot数</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">配信可能bot数</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">操作</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((group) => (
                <Fragment key={group.id}>
                  <tr className="border-b last:border-b-0 hover:bg-gray-50">
                    <td className="px-2 py-3">
                      {editingGroupId === group.id ? (
                        <input
                          type="text"
                          value={editForm.name}
                          onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                          className="w-full border rounded px-2 py-1 text-sm"
                        />
                      ) : (
                        group.name
                      )}
                    </td>
                    <td className="px-2 py-3 text-gray-600">{group.organizationName}</td>
                    <td className="px-2 py-3 text-center">
                      <button
                        onClick={() => handleToggleEnabled(group)}
                        aria-label={group.enabled ? '無効化' : '有効化'}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          group.enabled ? 'bg-[#06C755]' : 'bg-gray-300'
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${
                            group.enabled ? 'translate-x-5' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </td>
                    <td className="px-2 py-3 text-right whitespace-nowrap">
                      {editingGroupId === group.id ? (
                        <input
                          type="number"
                          value={editForm.expectedRecipientCount}
                          onChange={(e) => setEditForm({ ...editForm, expectedRecipientCount: e.target.value })}
                          className="w-20 border rounded px-2 py-1 text-sm text-right"
                        />
                      ) : (
                        group.expectedRecipientCount ?? '未設定'
                      )}
                    </td>
                    <td className="px-2 py-3 text-right">{group.botCount}</td>
                    <td className="px-2 py-3 text-right">{group.readyBotCount}</td>
                    <td className="px-2 py-3 text-right whitespace-nowrap">
                      <div className="flex justify-end gap-1">
                        {editingGroupId === group.id ? (
                          <>
                            <button
                              onClick={() => handleSaveEdit(group.id)}
                              className="px-2 py-1 text-xs bg-[#4a6b5a] text-white rounded hover:bg-[#3d5a4c]"
                            >
                              保存
                            </button>
                            <button
                              onClick={cancelEdit}
                              className="px-2 py-1 text-xs bg-gray-100 rounded hover:bg-gray-200"
                            >
                              キャンセル
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              onClick={() => startEdit(group)}
                              className="px-2 py-1 text-xs bg-gray-100 rounded hover:bg-gray-200"
                            >
                              編集
                            </button>
                            <button
                              onClick={() => handleSelectGroup(group.id)}
                              className="px-2 py-1 text-xs bg-[#4a6b5a] text-white rounded hover:bg-[#3d5a4c]"
                            >
                              {selectedGroupId === group.id ? '閉じる' : '詳細'}
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                  {selectedGroupId === group.id && (
                    <tr>
                      <td colSpan={7} className="px-2 py-4 bg-gray-50">
                        <GroupDetail
                          group={group}
                          status={status}
                          logs={logs}
                          loading={detailLoading}
                          error={detailError}
                          assignChannelId={assignChannelId}
                          onAssignChannelIdChange={setAssignChannelId}
                          onAssignBot={(e) => handleAssignBot(e, group.id)}
                          onUnassignBot={(channelId) => handleUnassignBot(group.id, channelId)}
                        />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
              {groups.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-gray-500">
                    配信グループが登録されていません
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
};

const GroupDetail = ({
  group,
  status,
  logs,
  loading,
  error,
  assignChannelId,
  onAssignChannelIdChange,
  onAssignBot,
  onUnassignBot,
}) => {
  if (loading) {
    return <p className="text-sm text-gray-500">読み込み中...</p>;
  }

  return (
    <div className="space-y-4">
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {/* bot割当フォーム */}
      <form onSubmit={onAssignBot} className="flex items-end gap-2">
        <div>
          <label className="block text-sm text-gray-600 mb-1">割当チャネルID</label>
          <input
            type="number"
            value={assignChannelId}
            onChange={(e) => onAssignChannelIdChange(e.target.value)}
            className="border rounded-lg px-3 py-2 text-sm"
            placeholder="チャネルID"
          />
        </div>
        <button type="submit" className="bg-[#4a6b5a] text-white py-2 px-4 rounded-lg text-sm hover:bg-[#3d5a4c]">
          割当
        </button>
      </form>

      {/* 稼働状況 */}
      {status && (
        <div className="bg-white border rounded-lg p-4 space-y-3">
          <h3 className="font-semibold text-gray-700">稼働状況</h3>

          {status.exhausted && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-red-600" />
              <p className="text-red-800 text-sm font-medium">
                全botが枯渇しています。今月はこれ以上配信できません。
              </p>
            </div>
          )}

          <div className="text-sm text-gray-600 flex flex-wrap gap-4">
            <span>今月あと配信可能回数: <strong>{status.remainingBroadcasts}</strong></span>
            <span>想定受信数: <strong>{status.expectedRecipientCount}</strong></span>
          </div>

          <div className="space-y-2">
            {status.bots.map((bot) => {
              const quotaPercent =
                bot.monthlyMessageCount + bot.remainingQuota > 0
                  ? Math.max(
                      0,
                      Math.min(
                        100,
                        (bot.remainingQuota / (bot.monthlyMessageCount + bot.remainingQuota)) * 100
                      )
                    )
                  : 0;
              const isNext = status.nextBotChannelId === bot.channelId;
              return (
                <div
                  key={bot.channelId}
                  className={`border rounded-lg p-3 ${isNext ? 'border-[#4a6b5a] bg-[#f0f4f1]' : 'border-gray-200'}`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      {isNext && (
                        <span title="次に配信するbot">
                          <Star className="h-4 w-4 text-[#4a6b5a] flex-shrink-0" />
                        </span>
                      )}
                      <span className="font-medium text-gray-700 truncate">
                        {bot.channelName || bot.lineChannelId}
                      </span>
                      {!bot.groupIdCaptured && (
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 whitespace-nowrap">
                          未参加
                        </span>
                      )}
                      {!bot.enabled && (
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-500 whitespace-nowrap">
                          無効
                        </span>
                      )}
                    </div>
                    <button
                      onClick={() => onUnassignBot(bot.channelId)}
                      title="割当解除"
                      className="p-1 text-red-600 hover:bg-red-50 rounded flex-shrink-0"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                  <div className="mt-2 text-xs text-gray-500 flex items-center justify-between">
                    <span>当月送信数: {bot.monthlyMessageCount}</span>
                    <span>残枠: {bot.remainingQuota}</span>
                  </div>
                  <div className="mt-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[#06C755]"
                      style={{ width: `${quotaPercent}%` }}
                    />
                  </div>
                </div>
              );
            })}
            {status.bots.length === 0 && (
              <p className="text-sm text-gray-500">botが割り当てられていません</p>
            )}
          </div>
        </div>
      )}

      {/* 配信ログ */}
      {logs && (
        <div className="bg-white border rounded-lg p-4 space-y-3">
          <h3 className="font-semibold text-gray-700">配信ログ</h3>

          {logs.hasRecentSkip && (
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-yellow-600" />
              <p className="text-yellow-800 text-sm">
                直近にスキップされた配信があります。ご確認ください。
              </p>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">日時</th>
                  <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">セッション</th>
                  <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">使用bot</th>
                  <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">受信数</th>
                  <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">状態</th>
                  <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">エラー</th>
                </tr>
              </thead>
              <tbody>
                {[...logs.logs]
                  .sort((a, b) => new Date(b.sentAt) - new Date(a.sentAt))
                  .map((log) => (
                    <tr key={log.id} className="border-b last:border-b-0">
                      <td className="px-2 py-2 whitespace-nowrap">{log.sentAt}</td>
                      <td className="px-2 py-2 whitespace-nowrap">{log.sessionId}</td>
                      <td className="px-2 py-2 whitespace-nowrap">{log.lineChannelId}</td>
                      <td className="px-2 py-2 text-right whitespace-nowrap">{log.recipientCount}</td>
                      <td className="px-2 py-2 whitespace-nowrap">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                            STATUS_STYLES[log.status] || 'bg-gray-100 text-gray-500'
                          }`}
                        >
                          {STATUS_LABELS[log.status] || log.status}
                        </span>
                      </td>
                      <td className="px-2 py-2 text-gray-500">{log.errorMessage || '-'}</td>
                    </tr>
                  ))}
                {logs.logs.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-gray-500">
                      配信ログはまだありません
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {group && !status && !logs && !loading && (
        <p className="text-sm text-gray-500">稼働状況の取得に失敗しました</p>
      )}
    </div>
  );
};

export default CardDivisionBroadcastAdmin;
