import { useState, useEffect } from 'react';
import { lineAPI } from '../../api';
import { Plus, Ban, Play, Unlink, AlertCircle } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const LineChannelAdmin = () => {
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    channelName: '',
    lineChannelId: '',
    channelSecret: '',
    channelAccessToken: '',
    basicId: '',
  });

  useEffect(() => {
    fetchChannels();
  }, []);

  const fetchChannels = async () => {
    try {
      setLoading(true);
      const res = await lineAPI.getChannels();
      setChannels(res.data);
    } catch (err) {
      setError('チャネル一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateChannel = async (e) => {
    e.preventDefault();
    try {
      await lineAPI.createChannel(form);
      setShowForm(false);
      setForm({ channelName: '', lineChannelId: '', channelSecret: '', channelAccessToken: '', basicId: '' });
      await fetchChannels();
    } catch (err) {
      setError('チャネルの登録に失敗しました');
    }
  };

  const handleDisable = async (channelId) => {
    if (!confirm('このチャネルを無効にしますか？')) return;
    try {
      await lineAPI.disableChannel(channelId);
      await fetchChannels();
    } catch (err) {
      setError('チャネルの無効化に失敗しました');
    }
  };

  const handleEnable = async (channelId) => {
    try {
      await lineAPI.enableChannel(channelId);
      await fetchChannels();
    } catch (err) {
      setError('チャネルの有効化に失敗しました');
    }
  };

  const handleForceRelease = async (channelId) => {
    if (!confirm('このチャネルの割り当てを強制解除しますか？')) return;
    try {
      await lineAPI.forceReleaseChannel(channelId);
      await fetchChannels();
    } catch (err) {
      setError('割り当て解除に失敗しました');
    }
  };

  const statusBadge = (status) => {
    const styles = {
      AVAILABLE: 'bg-green-100 text-green-800',
      ASSIGNED: 'bg-yellow-100 text-yellow-800',
      LINKED: 'bg-blue-100 text-blue-800',
      DISABLED: 'bg-gray-100 text-gray-500',
    };
    return (
      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${styles[status] || 'bg-gray-100'}`}>
        {status}
      </span>
    );
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-4xl mx-auto p-4 pt-16 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-800">LINEチャネル管理</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-1 bg-[#4a6b5a] text-white py-2 px-3 rounded-lg text-sm hover:bg-[#3d5a4c] transition-colors"
        >
          <Plus className="h-4 w-4" />
          新規登録
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {showForm && (
        <form onSubmit={handleCreateChannel} className="bg-white border rounded-lg p-4 space-y-3">
          <h2 className="font-semibold text-gray-700">チャネル登録</h2>
          {[
            { key: 'channelName', label: '管理名', required: false },
            { key: 'lineChannelId', label: 'チャネルID', required: true },
            { key: 'channelSecret', label: 'チャネルシークレット', required: true },
            { key: 'channelAccessToken', label: 'アクセストークン', required: true },
            { key: 'basicId', label: 'ベーシックID（例: @111aaaaa）', required: false },
          ].map(({ key, label, required }) => (
            <div key={key}>
              <label className="block text-sm text-gray-600 mb-1">{label}{required && ' *'}</label>
              <input
                type="text"
                value={form[key]}
                onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                required={required}
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
            </div>
          ))}
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

      {/* チャネル一覧 */}
      <div className="bg-white rounded-lg border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">ID</th>
                <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">ステータス</th>
                <th className="text-left px-2 py-2 text-gray-600 whitespace-nowrap">割り当て</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">月送信数</th>
                <th className="text-right px-2 py-2 text-gray-600 whitespace-nowrap">操作</th>
              </tr>
            </thead>
            <tbody>
              {channels.map((ch) => (
                <tr key={ch.id} className="border-b last:border-b-0 hover:bg-gray-50">
                  <td className="px-2 py-3">{ch.channelName || ch.lineChannelId}</td>
                  <td className="px-2 py-3">{statusBadge(ch.status)}</td>
                  <td className="px-2 py-3 text-gray-600">{ch.assignedPlayerName || '-'}</td>
                  <td className="px-2 py-3 text-right whitespace-nowrap">{ch.monthlyMessageCount}/200</td>
                  <td className="px-2 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      {ch.status === 'DISABLED' ? (
                        <button onClick={() => handleEnable(ch.id)} title="有効化"
                          className="p-1 text-green-600 hover:bg-green-50 rounded">
                          <Play className="h-4 w-4" />
                        </button>
                      ) : ch.status !== 'DISABLED' && (
                        <button onClick={() => handleDisable(ch.id)} title="無効化"
                          className="p-1 text-red-600 hover:bg-red-50 rounded">
                          <Ban className="h-4 w-4" />
                        </button>
                      )}
                      {(ch.status === 'ASSIGNED' || ch.status === 'LINKED') && (
                        <button onClick={() => handleForceRelease(ch.id)} title="強制解除"
                          className="p-1 text-orange-600 hover:bg-orange-50 rounded">
                          <Unlink className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {channels.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-500">
                    チャネルが登録されていません
                  </td>
                </tr>
              )}
            </tbody>
          </table>
      </div>
    </div>
  );
};

export default LineChannelAdmin;
