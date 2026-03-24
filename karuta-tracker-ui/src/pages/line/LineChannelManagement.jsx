import { useEffect, useState } from 'react';
import { lineAPI } from '../../api/line';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * LINEチャネル管理画面（管理者向け）
 */
export default function LineChannelManagement() {
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAddForm, setShowAddForm] = useState(false);
  const [newChannel, setNewChannel] = useState({
    channelName: '', lineChannelId: '', channelSecret: '',
    channelAccessToken: '', friendAddUrl: '', qrCodeUrl: '',
  });

  useEffect(() => {
    fetchChannels();
  }, []);

  const fetchChannels = async () => {
    setLoading(true);
    try {
      const res = await lineAPI.getChannels();
      setChannels(res.data);
    } catch (err) {
      console.error('Failed to fetch channels:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      await lineAPI.createChannel(newChannel);
      setShowAddForm(false);
      setNewChannel({ channelName: '', lineChannelId: '', channelSecret: '', channelAccessToken: '', friendAddUrl: '', qrCodeUrl: '' });
      fetchChannels();
    } catch (err) {
      console.error('Failed to create channel:', err);
    }
  };

  const handleAction = async (id, action) => {
    const confirmMsg = {
      disable: 'このチャネルを無効にしますか？',
      enable: 'このチャネルを有効にしますか？',
      release: 'このチャネルの割り当てを強制解除しますか？',
    };
    if (!window.confirm(confirmMsg[action])) return;

    try {
      if (action === 'disable') await lineAPI.disableChannel(id);
      else if (action === 'enable') await lineAPI.enableChannel(id);
      else if (action === 'release') await lineAPI.releaseChannel(id);
      fetchChannels();
    } catch (err) {
      console.error(`Failed to ${action} channel:`, err);
    }
  };

  const statusBadge = (status) => {
    const styles = {
      AVAILABLE: 'bg-green-100 text-green-800',
      ASSIGNED: 'bg-yellow-100 text-yellow-800',
      LINKED: 'bg-blue-100 text-blue-800',
      DISABLED: 'bg-gray-100 text-gray-800',
    };
    const labels = { AVAILABLE: '空き', ASSIGNED: '割当済', LINKED: '連携済', DISABLED: '無効' };
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${styles[status] || ''}`}>
        {labels[status] || status}
      </span>
    );
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">LINEチャネル管理</h1>
        <button
          onClick={() => setShowAddForm(!showAddForm)}
          className="bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-600 text-sm"
        >
          {showAddForm ? 'キャンセル' : 'チャネル追加'}
        </button>
      </div>

      {/* 統計 */}
      <div className="grid grid-cols-4 gap-3 mb-6">
        {['AVAILABLE', 'ASSIGNED', 'LINKED', 'DISABLED'].map((s) => (
          <div key={s} className="bg-white rounded-lg shadow p-3 text-center">
            <div className="text-2xl font-bold">{channels.filter((c) => c.status === s).length}</div>
            <div className="text-xs text-gray-500">{statusBadge(s)}</div>
          </div>
        ))}
      </div>

      {/* 追加フォーム */}
      {showAddForm && (
        <form onSubmit={handleCreate} className="bg-white rounded-lg shadow p-4 mb-6">
          <h2 className="text-lg font-semibold mb-3">チャネル追加</h2>
          <div className="space-y-3">
            {[
              { key: 'channelName', label: '管理名', required: false },
              { key: 'lineChannelId', label: 'チャネルID', required: true },
              { key: 'channelSecret', label: 'チャネルシークレット', required: true },
              { key: 'channelAccessToken', label: 'アクセストークン', required: true },
              { key: 'friendAddUrl', label: '友だち追加URL', required: false },
              { key: 'qrCodeUrl', label: 'QRコード画像URL', required: false },
            ].map(({ key, label, required }) => (
              <div key={key}>
                <label className="block text-sm text-gray-600 mb-1">{label}{required && ' *'}</label>
                <input
                  type="text"
                  value={newChannel[key]}
                  onChange={(e) => setNewChannel({ ...newChannel, [key]: e.target.value })}
                  required={required}
                  className="w-full border rounded-lg px-3 py-2 text-sm"
                />
              </div>
            ))}
          </div>
          <button type="submit" className="mt-4 bg-green-500 text-white px-4 py-2 rounded-lg hover:bg-green-600">
            登録
          </button>
        </form>
      )}

      {/* チャネル一覧 */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="px-4 py-3 text-left">ID</th>
              <th className="px-4 py-3 text-left">名前</th>
              <th className="px-4 py-3 text-left">ステータス</th>
              <th className="px-4 py-3 text-left">割り当てユーザー</th>
              <th className="px-4 py-3 text-right">月間送信数</th>
              <th className="px-4 py-3 text-center">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {channels.map((ch) => (
              <tr key={ch.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">{ch.id}</td>
                <td className="px-4 py-3">{ch.channelName || '-'}</td>
                <td className="px-4 py-3">{statusBadge(ch.status)}</td>
                <td className="px-4 py-3">{ch.assignedPlayerName || '-'}</td>
                <td className="px-4 py-3 text-right">{ch.monthlyMessageCount}/200</td>
                <td className="px-4 py-3 text-center">
                  <div className="flex gap-1 justify-center">
                    {ch.status === 'DISABLED' ? (
                      <button onClick={() => handleAction(ch.id, 'enable')}
                        className="text-green-600 hover:text-green-800 text-xs">有効化</button>
                    ) : (
                      <button onClick={() => handleAction(ch.id, 'disable')}
                        className="text-gray-600 hover:text-gray-800 text-xs">無効化</button>
                    )}
                    {(ch.status === 'ASSIGNED' || ch.status === 'LINKED') && (
                      <button onClick={() => handleAction(ch.id, 'release')}
                        className="text-red-600 hover:text-red-800 text-xs">解除</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {channels.length === 0 && (
          <p className="text-gray-500 text-center py-8">チャネルが登録されていません</p>
        )}
      </div>
    </div>
  );
}
