import { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI, practiceAPI, pairingAPI } from '../../api';
import { Trophy, Save, X, AlertCircle, Users, Lock, Home, PlusSquare, Calendar, User } from 'lucide-react';

const MatchForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentPlayer } = useAuth();
  const isEdit = !!id;

  // location.stateから初期値を取得
  const initialData = location.state || {};

  const [formData, setFormData] = useState({
    matchDate: initialData.matchDate || new Date().toISOString().split('T')[0],
    opponentName: initialData.opponentName || '',
    opponentId: initialData.opponentId || null, // 詳細試合作成用の対戦相手ID
    result: '勝ち',
    scoreDifference: 0,
    matchNumber: initialData.matchNumber || 1,
    notes: '',
  });

  const [players, setPlayers] = useState([]);
  const [availablePlayers, setAvailablePlayers] = useState([]);
  const [practiceSession, setPracticeSession] = useState(null);
  const [practiceSessions, setPracticeSessions] = useState([]);
  const [pairing, setPairing] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState('');
  const [participatingMatchNumbers, setParticipatingMatchNumbers] = useState([]); // 参加する試合番号リスト
  const [isExistingMatch, setIsExistingMatch] = useState(false); // 既存試合かどうか

  useEffect(() => {
    const fetchData = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];

        // 選手一覧・今日の練習セッション・（編集時）試合データを並列取得
        const promises = [
          playerAPI.getAll(),
          practiceAPI.getByDate(today).catch(() => ({ data: null }))
        ];
        if (isEdit) promises.push(matchAPI.getById(id));
        const [playersRes, todaySessionRes, matchRes] = await Promise.all(promises);

        setPlayers(
          playersRes.data.filter((p) => p.id !== currentPlayer.id)
        );

        const todaySessions = todaySessionRes.data ? [todaySessionRes.data] : [];
        setPracticeSessions(todaySessions);

        // 編集モードの場合、既存データを反映
        if (isEdit && matchRes) {
          const match = matchRes.data;

          // 試合日が今日でない場合はエラー
          if (match.matchDate !== today) {
            setError('過去の試合記録は編集できません。試合記録の編集は当日のみ可能です。');
            setInitialLoading(false);
            return;
          }

          setFormData({
            matchDate: match.matchDate,
            opponentName: match.opponentName,
            result: match.result,
            scoreDifference: match.scoreDifference,
            matchNumber: match.matchNumber,
            notes: match.notes || '',
          });
        }

        setInitialLoading(false);
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
        setInitialLoading(false);
      }
    };

    fetchData();
  }, [id, isEdit, currentPlayer]);

  // 試合日が変更されたら、その日の練習セッションを取得
  useEffect(() => {
    const fetchPracticeSession = async () => {
      if (!formData.matchDate) return;

      try {
        const response = await practiceAPI.getByDate(formData.matchDate);
        if (response.data) {
          setPracticeSession(response.data);

          // 練習セッションがある場合、参加者を取得
          const participantsRes = await practiceAPI.getParticipants(response.data.id);
          const participants = participantsRes.data.filter(p => p.id !== currentPlayer.id);
          setAvailablePlayers(participants);

          // ①自分が参加する試合番号を把握
          const myMatchNumbers = [];
          if (response.data.matchParticipants) {
            for (const [matchNum, playerNames] of Object.entries(response.data.matchParticipants)) {
              if (playerNames.includes(currentPlayer.name)) {
                myMatchNumbers.push(parseInt(matchNum));
              }
            }
          }
          myMatchNumbers.sort((a, b) => a - b);
          setParticipatingMatchNumbers(myMatchNumbers);

          // ②未入力の最小試合番号をデフォルトに設定（編集モードでない場合のみ）
          if (!isEdit && myMatchNumbers.length > 0) {
            // 当日の全試合記録を取得
            const matchPromises = myMatchNumbers.map(num =>
              matchAPI.getByPlayerDateAndMatchNumber(currentPlayer.id, formData.matchDate, num)
                .then(res => ({ matchNumber: num, exists: true, data: res.data }))
                .catch(() => ({ matchNumber: num, exists: false }))
            );
            const matchResults = await Promise.all(matchPromises);

            // 未入力の最小試合番号を探す
            const unrecordedMatch = matchResults.find(result => !result.exists);
            const defaultMatchNumber = unrecordedMatch ? unrecordedMatch.matchNumber : myMatchNumbers[0];

            setFormData(prev => ({ ...prev, matchNumber: defaultMatchNumber }));
          }
        } else {
          setPracticeSession(null);
          setAvailablePlayers([]);
          setParticipatingMatchNumbers([]);
        }
      } catch (err) {
        setPracticeSession(null);
        setAvailablePlayers([]);
        setParticipatingMatchNumbers([]);
      }
    };

    if (players.length > 0 && !isEdit) {
      fetchPracticeSession();
    }
  }, [formData.matchDate, players.length, currentPlayer.id, currentPlayer.name, isEdit]);

  // 試合番号が変更されたら、対戦組み合わせと既存試合記録をチェック
  useEffect(() => {
    const checkPairingAndExistingMatch = async () => {
      if (!formData.matchDate || !formData.matchNumber) return;

      try {
        // ③既存の試合記録を確認
        const existingMatchRes = await matchAPI.getByPlayerDateAndMatchNumber(
          currentPlayer.id,
          formData.matchDate,
          formData.matchNumber
        ).catch(() => null);

        if (existingMatchRes && existingMatchRes.data) {
          // 既存記録がある場合、フォームに反映
          const match = existingMatchRes.data;
          setIsExistingMatch(true);
          setFormData(prev => ({
            ...prev,
            opponentName: match.opponentName,
            opponentId: match.opponentId || null,
            result: match.result,
            scoreDifference: match.scoreDifference,
            notes: match.notes || ''
          }));
        } else {
          // 既存記録がない場合、対戦組み合わせをチェック
          setIsExistingMatch(false);

          const response = await pairingAPI.getByDateAndMatchNumber(
            formData.matchDate,
            formData.matchNumber
          );

          if (response.data && response.data.length > 0) {
            // 自分が含まれる対戦組み合わせを探す
            const myPairing = response.data.find(
              p => p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id
            );

            if (myPairing) {
              setPairing(myPairing);
              // 対戦相手を自動設定
              const opponentId = myPairing.player1Id === currentPlayer.id
                ? myPairing.player2Id
                : myPairing.player1Id;
              const opponentName = myPairing.player1Id === currentPlayer.id
                ? myPairing.player2Name
                : myPairing.player1Name;

              setFormData(prev => ({
                ...prev,
                opponentId: opponentId,
                opponentName: opponentName,
                result: '勝ち',
                scoreDifference: 0,
                notes: ''
              }));
            } else {
              // 自分の組み合わせがない場合
              setPairing(null);
              setFormData(prev => ({
                ...prev,
                opponentId: null,
                opponentName: '',
                result: '勝ち',
                scoreDifference: 0,
                notes: ''
              }));
            }
          } else {
            // 組み合わせが存在しない場合
            setPairing(null);
            setFormData(prev => ({
              ...prev,
              opponentId: null,
              opponentName: '',
              result: '勝ち',
              scoreDifference: 0,
              notes: ''
            }));
          }
        }
      } catch (err) {
        setIsExistingMatch(false);
        setPairing(null);
      }
    };

    checkPairingAndExistingMatch();
  }, [formData.matchDate, formData.matchNumber, currentPlayer]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // バリデーション
    if (!formData.opponentName) {
      setError('対戦相手を選択してください');
      return;
    }

    setLoading(true);

    try {
      if (isEdit) {
        // 編集モードは簡易版APIを使用
        const submitData = {
          ...formData,
          playerId: currentPlayer.id,
          scoreDifference: parseInt(formData.scoreDifference),
          matchNumber: parseInt(formData.matchNumber),
        };
        await matchAPI.update(id, submitData);
        navigate('/matches');
      } else {
        // 新規登録時：opponentIdがあれば詳細版API、なければ簡易版APIを使用
        if (formData.opponentId) {
          // 詳細試合作成API（両選手がシステムに登録されている場合）
          const player1Id = currentPlayer.id;
          const player2Id = formData.opponentId;
          const winnerId = formData.result === '勝ち' ? currentPlayer.id : formData.opponentId;

          const detailedData = {
            matchDate: formData.matchDate,
            matchNumber: parseInt(formData.matchNumber),
            player1Id: player1Id,
            player2Id: player2Id,
            winnerId: winnerId,
            scoreDifference: parseInt(formData.scoreDifference),
            notes: formData.notes,
            createdBy: currentPlayer.id,
            updatedBy: currentPlayer.id
          };

          await matchAPI.createDetailed(detailedData);
        } else {
          // 簡易試合作成API（対戦相手が未登録の場合）
          const submitData = {
            ...formData,
            playerId: currentPlayer.id,
            scoreDifference: parseInt(formData.scoreDifference),
            matchNumber: parseInt(formData.matchNumber),
          };
          await matchAPI.create(submitData);
        }

        // 新規登録の場合はホーム画面に遷移して今日の対戦カードを更新
        navigate('/');
      }
    } catch (err) {
      console.error('保存エラー:', err);

      // 重複エラー（409 Conflict）の場合
      if (err.response?.status === 409 && err.response?.data?.existingMatchId) {
        const existingMatchId = err.response.data.existingMatchId;
        const confirmMessage = `${err.response.data.message}\n\n既存の記録を上書きしますか？`;

        if (window.confirm(confirmMessage)) {
          try {
            const submitData = {
              ...formData,
              playerId: currentPlayer.id,
              scoreDifference: parseInt(formData.scoreDifference),
              matchNumber: parseInt(formData.matchNumber),
            };

            await matchAPI.update(existingMatchId, submitData);
            navigate('/');
          } catch (updateErr) {
            console.error('更新エラー:', updateErr);
            setError(
              updateErr.response?.data?.message || '試合記録の更新に失敗しました'
            );
          }
        }
      } else {
        setError(
          err.response?.data?.message ||
            `試合記録の${isEdit ? '更新' : '登録'}に失敗しました`
        );
      }
    } finally {
      setLoading(false);
    }
  };

  // 初期ローディング中
  if (initialLoading) {
    return (
      <div className="min-h-screen bg-gray-50 pb-16 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  // 過去の試合記録編集エラーの場合
  if (error && isEdit) {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-lg shadow-sm p-6 space-y-6">
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <div className="flex justify-center">
            <button
              onClick={() => navigate('/')}
              className="flex items-center justify-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors font-medium"
            >
              ホームに戻る
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16 overflow-hidden">
      {/* ナビゲーションバー（日付表示と試合番号タブ） */}
      {practiceSessions.length > 0 && practiceSession && (
        <div className="bg-white shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
          <div className="text-center mb-3">
            <div className="text-lg font-semibold text-gray-900">
              {new Date(formData.matchDate).toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                weekday: 'short'
              })}
            </div>
          </div>

          {/* 試合番号タブ */}
          <div className="flex gap-2 overflow-x-auto pb-1">
            {(participatingMatchNumbers.length > 0
              ? participatingMatchNumbers
              : Array.from({ length: practiceSession.totalMatches }, (_, i) => i + 1)
            ).map((num) => (
              <button
                key={num}
                type="button"
                onClick={() => setFormData(prev => ({ ...prev, matchNumber: num }))}
                className={`flex-shrink-0 px-4 py-2 rounded-lg font-medium transition-colors ${
                  formData.matchNumber === num
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                第{num}試合
              </button>
            ))}
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="h-full p-3 space-y-3 overflow-hidden pt-32">
        {/* 今日が練習日でない場合の警告 */}
        {!isEdit && practiceSessions.length === 0 && (
          <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-center gap-2 text-yellow-800 mb-2">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span className="font-semibold">今日は練習日として登録されていません</span>
            </div>
            <p className="text-sm text-yellow-700">
              試合記録の登録は当日の練習日のみ可能です。練習日を登録してから試合記録を入力してください。
            </p>
          </div>
        )}

        {/* 既存試合の警告メッセージ */}
        {!isEdit && isExistingMatch && (
          <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg flex items-center gap-2 text-blue-700">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span className="text-sm">この試合は既に入力済みです。保存すると上書きされます。</span>
          </div>
        )}

        {/* 対戦相手 */}
        {practiceSession && (
          <div>
            <select
              value={formData.opponentId || ''}
              onChange={(e) => {
                const selectedPlayer = availablePlayers.find(p => p.id === parseInt(e.target.value));
                if (selectedPlayer) {
                  setFormData(prev => ({
                    ...prev,
                    opponentName: selectedPlayer.name,
                    opponentId: selectedPlayer.id
                  }));
                }
              }}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent text-base"
              required
            >
              <option value="">対戦相手を選択</option>
              {availablePlayers.map((player) => (
                <option key={player.id} value={player.id}>
                  {player.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* 結果 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            結果 <span className="text-red-500">*</span>
          </label>
          <div className="grid grid-cols-2 gap-2">
            {['勝ち', '負け'].map((result) => (
              <button
                key={result}
                type="button"
                onClick={() =>
                  setFormData((prev) => ({ ...prev, result }))
                }
                className={`px-4 py-2 rounded-lg border-2 font-medium transition-all ${
                  formData.result === result
                    ? result === '勝ち'
                      ? 'border-green-500 bg-green-50 text-green-700'
                      : 'border-red-500 bg-red-50 text-red-700'
                    : 'border-gray-200 text-gray-600 hover:border-gray-300'
                }`}
              >
                {result}
              </button>
            ))}
          </div>
        </div>

        {/* 枚数差 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            枚数差 <span className="text-red-500">*</span>
          </label>
          <select
            name="scoreDifference"
            value={formData.scoreDifference}
            onChange={handleChange}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent text-base"
            required
          >
            {Array.from({ length: 26 }, (_, i) => i).map((num) => (
              <option key={num} value={num}>
                {num} 枚
              </option>
            ))}
          </select>
        </div>

        {/* メモ */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            メモ
          </label>
          <textarea
            name="notes"
            value={formData.notes}
            onChange={handleChange}
            rows="3"
            placeholder="試合の感想、反省点など..."
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
          ></textarea>
        </div>

        {/* ボタン */}
        {practiceSessions.length > 0 && (
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={loading}
            className="flex-1 flex items-center justify-center gap-2 bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-medium"
          >
            <Save className="w-4 h-4" />
            {loading
              ? '保存中...'
              : isEdit
              ? '更新'
              : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/matches')}
            className="flex items-center justify-center gap-2 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-200 transition-colors font-medium"
          >
            <X className="w-4 h-4" />
            キャンセル
          </button>
        </div>
        )}
      </form>

      {/* ボトムナビゲーション */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 z-50">
        <div className="flex justify-around items-center h-16 max-w-7xl mx-auto">
          <Link
            to="/"
            className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
          >
            <Home className="w-6 h-6 text-gray-400" strokeWidth={2} />
            <span className="text-xs mt-1 text-gray-500">Home</span>
          </Link>
          <Link
            to="/matches/new"
            className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
          >
            <PlusSquare className="w-6 h-6 text-primary-600" strokeWidth={2.5} />
            <span className="text-xs mt-1 text-primary-600 font-semibold">Add</span>
          </Link>
          <Link
            to="/matches"
            className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
          >
            <Trophy className="w-6 h-6 text-gray-400" strokeWidth={2} />
            <span className="text-xs mt-1 text-gray-500">Results</span>
          </Link>
          <Link
            to="/practice"
            className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
          >
            <Calendar className="w-6 h-6 text-gray-400" strokeWidth={2} />
            <span className="text-xs mt-1 text-gray-500">Schedule</span>
          </Link>
          <Link
            to="/profile"
            className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
          >
            <User className="w-6 h-6 text-gray-400" strokeWidth={2} />
            <span className="text-xs mt-1 text-gray-500">Profile</span>
          </Link>
        </div>
      </nav>
    </div>
  );
};

export default MatchForm;
