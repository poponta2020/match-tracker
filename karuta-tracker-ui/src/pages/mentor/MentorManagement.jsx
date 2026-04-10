import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { mentorRelationshipAPI } from '../../api/mentorRelationship';
import { organizationAPI } from '../../api/organizations';
import { playerAPI } from '../../api/players';
import { getCurrentPlayer } from '../../utils/auth';
import { Users, UserPlus, UserCheck, UserX, ChevronLeft, ChevronRight, Clock } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

export default function MentorManagement() {
  const navigate = useNavigate();
  const currentPlayer = getCurrentPlayer();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [myMentors, setMyMentors] = useState([]);
  const [myMentees, setMyMentees] = useState([]);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [myOrgs, setMyOrgs] = useState([]);
  const [allPlayers, setAllPlayers] = useState([]);
  const [showNominateForm, setShowNominateForm] = useState(false);
  const [selectedOrgId, setSelectedOrgId] = useState('');
  const [selectedMentorId, setSelectedMentorId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const [mentorsRes, menteesRes, pendingRes, orgsRes, playersRes] = await Promise.all([
        mentorRelationshipAPI.getMyMentors(),
        mentorRelationshipAPI.getMyMentees(),
        mentorRelationshipAPI.getPending(),
        organizationAPI.getAll(),
        playerAPI.getAll(),
      ]);
      setMyMentors(mentorsRes.data);
      setMyMentees(menteesRes.data);
      setPendingRequests(pendingRes.data);

      // localStorageのcurrentPlayerにorganizationIdsがない場合（デプロイ前ログインユーザー）、
      // API応答から取得してフォールバックする
      const currentFromApi = playersRes.data.find(p => p.id === currentPlayer.id);
      const orgIds = currentPlayer.organizationIds || currentFromApi?.organizationIds || [];
      const playerOrgs = orgsRes.data.filter(org => orgIds.includes(org.id));
      setMyOrgs(playerOrgs);
      setAllPlayers(playersRes.data);
    } catch (e) {
      setError('データの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [currentPlayer.id, currentPlayer.organizationIds]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleNominate = async () => {
    if (!selectedOrgId || !selectedMentorId) return;
    try {
      setSubmitting(true);
      setError(null);
      await mentorRelationshipAPI.create(Number(selectedMentorId), Number(selectedOrgId));
      setSuccess('メンターを指名しました。承認をお待ちください。');
      setShowNominateForm(false);
      setSelectedOrgId('');
      setSelectedMentorId('');
      await fetchData();
    } catch (e) {
      setError(e.response?.data?.message || 'メンター指名に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      setError(null);
      await mentorRelationshipAPI.approve(id);
      setSuccess('メンター指名を承認しました');
      await fetchData();
    } catch (e) {
      setError(e.response?.data?.message || '承認に失敗しました');
    }
  };

  const handleReject = async (id) => {
    try {
      setError(null);
      await mentorRelationshipAPI.reject(id);
      setSuccess('メンター指名を拒否しました');
      await fetchData();
    } catch (e) {
      setError(e.response?.data?.message || '拒否に失敗しました');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('メンター関係を解除しますか？')) return;
    try {
      setError(null);
      await mentorRelationshipAPI.delete(id);
      setSuccess('メンター関係を解除しました');
      await fetchData();
    } catch (e) {
      setError(e.response?.data?.message || '解除に失敗しました');
    }
  };

  const availableMentors = allPlayers.filter(
    p => p.id !== currentPlayer.id && p.organizationIds?.includes(Number(selectedOrgId))
  );

  if (loading) return <LoadingScreen />;

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <div className="bg-[#4a6b5a] text-white p-4 flex items-center gap-3">
        <button onClick={() => navigate('/settings')} className="p-1">
          <ChevronLeft size={24} />
        </button>
        <Users size={24} />
        <h1 className="text-lg font-bold">メンター管理</h1>
      </div>

      <div className="p-4 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 p-3 rounded-lg text-sm">
            {error}
          </div>
        )}
        {success && (
          <div className="bg-green-50 border border-green-200 text-green-700 p-3 rounded-lg text-sm">
            {success}
            <button onClick={() => setSuccess(null)} className="ml-2 underline">閉じる</button>
          </div>
        )}

        {/* 承認待ちリクエスト（メンター側） */}
        {pendingRequests.length > 0 && (
          <div className="bg-white rounded-lg shadow p-4">
            <h2 className="font-bold text-gray-800 mb-3 flex items-center gap-2">
              <Clock size={18} className="text-yellow-500" />
              承認待ちリクエスト
            </h2>
            <div className="space-y-3">
              {pendingRequests.map(req => (
                <div key={req.id} className="flex items-center justify-between bg-yellow-50 p-3 rounded-lg">
                  <div>
                    <p className="font-medium text-gray-800">{req.menteeName}</p>
                    <p className="text-xs text-gray-500">{req.organizationName}</p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApprove(req.id)}
                      className="bg-[#4a6b5a] text-white px-3 py-1 rounded text-sm flex items-center gap-1"
                    >
                      <UserCheck size={14} /> 承認
                    </button>
                    <button
                      onClick={() => handleReject(req.id)}
                      className="bg-gray-400 text-white px-3 py-1 rounded text-sm flex items-center gap-1"
                    >
                      <UserX size={14} /> 拒否
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 自分のメンター一覧（メンティー側） */}
        <div className="bg-white rounded-lg shadow p-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-bold text-gray-800 flex items-center gap-2">
              <UserCheck size={18} className="text-[#4a6b5a]" />
              マイメンター
            </h2>
            <button
              onClick={() => setShowNominateForm(!showNominateForm)}
              className="bg-[#4a6b5a] text-white px-3 py-1 rounded text-sm flex items-center gap-1"
            >
              <UserPlus size={14} /> メンター指名
            </button>
          </div>

          {showNominateForm && (
            <div className="bg-gray-50 p-3 rounded-lg mb-3 space-y-2">
              <select
                value={selectedOrgId}
                onChange={e => { setSelectedOrgId(e.target.value); setSelectedMentorId(''); }}
                className="w-full border rounded p-2 text-sm"
              >
                <option value="">組織を選択</option>
                {myOrgs.map(org => (
                  <option key={org.id} value={org.id}>{org.name}</option>
                ))}
              </select>
              {selectedOrgId && (
                <select
                  value={selectedMentorId}
                  onChange={e => setSelectedMentorId(e.target.value)}
                  className="w-full border rounded p-2 text-sm"
                >
                  <option value="">メンターを選択</option>
                  {availableMentors.map(p => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              )}
              <div className="flex gap-2">
                <button
                  onClick={handleNominate}
                  disabled={!selectedOrgId || !selectedMentorId || submitting}
                  className="bg-[#4a6b5a] text-white px-4 py-2 rounded text-sm disabled:opacity-50"
                >
                  {submitting ? '送信中...' : '指名する'}
                </button>
                <button
                  onClick={() => { setShowNominateForm(false); setSelectedOrgId(''); setSelectedMentorId(''); }}
                  className="bg-gray-300 text-gray-700 px-4 py-2 rounded text-sm"
                >
                  キャンセル
                </button>
              </div>
            </div>
          )}

          {myMentors.length === 0 ? (
            <p className="text-gray-500 text-sm">メンターはまだいません</p>
          ) : (
            <div className="space-y-2">
              {myMentors.map(m => (
                <div key={m.id} className="flex items-center justify-between bg-gray-50 p-3 rounded-lg">
                  <div>
                    <p className="font-medium text-gray-800">{m.mentorName}</p>
                    <p className="text-xs text-gray-500">
                      {m.organizationName}
                      {m.status === 'PENDING' && (
                        <span className="ml-2 text-yellow-600">（承認待ち）</span>
                      )}
                    </p>
                  </div>
                  <button
                    onClick={() => handleDelete(m.id)}
                    className="text-red-500 text-sm underline"
                  >
                    解除
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 自分のメンティー一覧（メンター側） */}
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-bold text-gray-800 mb-3 flex items-center gap-2">
            <Users size={18} className="text-[#4a6b5a]" />
            マイメンティー
          </h2>
          {myMentees.length === 0 ? (
            <p className="text-gray-500 text-sm">メンティーはまだいません</p>
          ) : (
            <div className="space-y-2">
              {myMentees.map(m => (
                <div key={m.id} className="flex items-center justify-between bg-gray-50 p-3 rounded-lg">
                  <button
                    onClick={() => navigate(`/matches?playerId=${m.menteeId}`)}
                    className="flex-1 text-left"
                  >
                    <p className="font-medium text-[#4a6b5a]">{m.menteeName}</p>
                    <p className="text-xs text-gray-500">{m.organizationName}</p>
                  </button>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleDelete(m.id)}
                      className="text-red-500 text-sm underline"
                    >
                      解除
                    </button>
                    <ChevronRight size={16} className="text-gray-400" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
