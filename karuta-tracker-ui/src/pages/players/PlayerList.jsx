import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { Users, Search, Edit, Trash2, UserPlus } from 'lucide-react';

const PlayerList = () => {
  const navigate = useNavigate();
  const [players, setPlayers] = useState([]);
  const [filteredPlayers, setFilteredPlayers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchPlayers();
  }, []);

  useEffect(() => {
    if (searchTerm) {
      const filtered = players.filter((player) =>
        player.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
      setFilteredPlayers(filtered);
    } else {
      setFilteredPlayers(players);
    }
  }, [searchTerm, players]);

  const fetchPlayers = async () => {
    try {
      setLoading(true);
      const response = await playerAPI.getAll();
      setPlayers(response.data);
      setFilteredPlayers(response.data);
    } catch (err) {
      console.error('Failed to fetch players:', err);
      setError('選手一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`${name}を削除してもよろしいですか？`)) {
      return;
    }

    try {
      await playerAPI.delete(id);
      alert('選手を削除しました');
      fetchPlayers();
    } catch (err) {
      console.error('Failed to delete player:', err);
      alert('選手の削除に失敗しました');
    }
  };

  const getGenderLabel = (gender) => {
    switch (gender) {
      case 'MALE':
        return '男性';
      case 'FEMALE':
        return '女性';
      case 'OTHER':
        return 'その他';
      default:
        return '未設定';
    }
  };

  const getDominantHandLabel = (hand) => {
    switch (hand) {
      case 'RIGHT':
        return '右利き';
      case 'LEFT':
        return '左利き';
      default:
        return '未設定';
    }
  };

  const getRankDisplay = (danRank, kyuRank) => {
    if (danRank) return danRank;
    if (kyuRank) return kyuRank;
    return '-';
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-600">読み込み中...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
            <Users className="w-8 h-8 text-primary-600" />
            選手一覧
          </h1>
          <p className="text-gray-600 mt-1">登録されている選手の一覧</p>
        </div>
        <button
          onClick={() => navigate('/register')}
          className="flex items-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors"
        >
          <UserPlus className="w-5 h-5" />
          新規登録
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 p-4 rounded-lg text-red-700">
          {error}
        </div>
      )}

      <div className="bg-white p-4 rounded-lg shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <Search className="w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="選手名で検索..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
        </div>

        <div className="text-sm text-gray-600 mb-4">
          {filteredPlayers.length}人の選手が見つかりました
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  名前
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  段位・級位
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  所属かるた会
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  性別
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ロール
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredPlayers.map((player) => (
                <tr
                  key={player.id}
                  className="hover:bg-gray-50 cursor-pointer"
                  onClick={() => navigate(`/players/${player.id}`)}
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="font-medium text-gray-900">{player.name}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {getRankDisplay(player.danRank, player.kyuRank)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {player.karutaClub || '-'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {getGenderLabel(player.gender)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span
                      className={`px-2 py-1 text-xs font-semibold rounded-full ${
                        player.role === 'ADMIN'
                          ? 'bg-purple-100 text-purple-800'
                          : 'bg-green-100 text-green-800'
                      }`}
                    >
                      {player.role === 'ADMIN' ? '管理者' : '一般ユーザー'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/players/${player.id}/edit`);
                        }}
                        className="text-primary-600 hover:text-primary-900 p-1"
                        title="編集"
                      >
                        <Edit className="w-5 h-5" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(player.id, player.name);
                        }}
                        className="text-red-600 hover:text-red-900 p-1"
                        title="削除"
                      >
                        <Trash2 className="w-5 h-5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {filteredPlayers.length === 0 && (
            <div className="text-center py-12 text-gray-500">
              {searchTerm ? '検索結果が見つかりませんでした' : '選手が登録されていません'}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PlayerList;
