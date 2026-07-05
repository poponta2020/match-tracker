import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI, practiceAPI, pairingAPI, byeActivityAPI } from '../../api';
import { AlertCircle, UserPlus, BookOpen, User, Eye, UsersRound, MoreHorizontal, UserX, Search, ChevronDown, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import { isHorizontalSwipe, resolveSwipe } from './swipeGesture';
import { scrollActiveTabIntoView } from './tabScroll';
import { kyuRankShortLabel } from '../../utils/rank';
import './MatchForm.css';

const MatchForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentPlayer } = useAuth();
  const isEdit = !!id;

  // location.stateから初期値を取得
  const initialData = location.state || {};
  // 遷移元（対戦組み合わせ画面のFAB等）で試合番号が明示指定されたか
  const hasExplicitMatchNumber = initialData.matchNumber != null;

  const [formData, setFormData] = useState({
    matchDate: initialData.matchDate || new Date().toISOString().split('T')[0],
    opponentName: initialData.opponentName || '',
    opponentId: initialData.opponentId || null,
    result: '勝ち',
    scoreDifference: 0,
    isLesson: false,
    matchNumber: initialData.matchNumber || 1,
    personalNotes: '',
    otetsukiCount: null,
  });

  const [players, setPlayers] = useState([]);
  const [practiceSession, setPracticeSession] = useState(null);
  const [practiceSessions, setPracticeSessions] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState('');
  const [participatingMatchNumbers, setParticipatingMatchNumbers] = useState([]);
  const [isExistingMatch, setIsExistingMatch] = useState(false);
  const [showParticipationDialog, setShowParticipationDialog] = useState(false);
  const [isRegistering, setIsRegistering] = useState(false);
  // 「未参加から検索」モーダル（当日未参加の全選手を簡易インクリメンタル検索）
  const [showSearchModal, setShowSearchModal] = useState(false);
  // 編集対象の試合が元々指導試合だったか（指導↔通常の変換時に詳細版APIへ振り分けるため）
  const [originalIsLesson, setOriginalIsLesson] = useState(false);

  // 抜け番活動関連
  const [isByeMatch, setIsByeMatch] = useState(false);
  const [manualByeMode, setManualByeMode] = useState(false); // 手動で抜け番モードに切り替えたか
  const [byeActivityType, setByeActivityType] = useState('');
  const [byeFreeText, setByeFreeText] = useState('');
  const [existingByeActivity, setExistingByeActivity] = useState(null); // 既存の抜け番記録

  // 試合番号スワイプ移動関連
  const [isDirty, setIsDirty] = useState(false); // 未保存の入力変更があるか（新規入力フローのみ）
  const [showSwitchConfirm, setShowSwitchConfirm] = useState(false); // 試合切替の確認ダイアログ
  const [pendingChange, setPendingChange] = useState(null); // 確認待ちの切替 { num, fromSwipe }
  const [slide, setSlide] = useState({ dir: null, key: 0 }); // スライドイン方向と再実行用キー
  const [slideStyle, setSlideStyle] = useState({});
  const tabBarRef = useRef(null);   // 試合番号タブバー（自動スクロール用）
  const contentRef = useRef(null);  // スワイプ検出対象のコンテンツ領域
  const swipeStart = useRef(null);  // タッチ開始座標

  // 初期ロード完了フラグ（タブ切り替え時の重複API呼び出し防止用）
  const initialLoadDone = useRef(false);
  // 全試合データのキャッシュ（タブ即時切替用）
  const matchDataCache = useRef({});   // matchNumber -> { exists, data }
  const pairingCache = useRef({});     // matchNumber -> myPairing or null
  const allPairingsCache = useRef([]); // 全ペアリングデータ
  const byeActivityCache = useRef({}); // matchNumber -> byeActivity or null（日付はformData.matchDateで固定のためキーに含めない）

  // useEffect 1: 選手一覧 + 練習セッション取得
  useEffect(() => {
    const fetchData = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];
        // location.stateで日付が指定されていればその日付、なければ今日
        const targetDate = initialData.matchDate || today;

        const promises = [
          playerAPI.getAll(),
          practiceAPI.getByDate(targetDate).catch(() => ({ data: null }))
        ];
        if (isEdit) promises.push(matchAPI.getById(id));
        const [playersRes, sessionRes, matchRes] = await Promise.all(promises);

        setPlayers(
          playersRes.data.filter((p) => p.id !== currentPlayer.id)
        );

        const sessions = sessionRes.data ? [sessionRes.data] : [];
        setPracticeSessions(sessions);

        // 練習セッションを直接セット（useEffect 2で再取得しない）
        if (sessionRes.data) {
          setPracticeSession(sessionRes.data);

          // 参加登録チェック（新規作成時のみ、今日の練習の場合のみ）
          if (!isEdit && targetDate === today) {
            try {
              const now = new Date();
              const participationsRes = await practiceAPI.getPlayerParticipations(
                currentPlayer.id, now.getFullYear(), now.getMonth() + 1
              );
              const sessionParticipations = participationsRes.data?.[sessionRes.data.id] || [];
              if (sessionParticipations.length === 0) {
                setShowParticipationDialog(true);
              }
            } catch (e) {
              // 参加状況取得に失敗しても結果入力は許可
              console.warn('参加状況の取得に失敗:', e);
            }
          }
        }

        if (isEdit && matchRes) {
          const match = matchRes.data;

          // 編集時に試合の日付がtargetDateと異なる場合、その日のセッションも取得
          if (match.matchDate !== targetDate) {
            try {
              const editSessionRes = await practiceAPI.getByDate(match.matchDate);
              if (editSessionRes.data) {
                setPracticeSessions(prev => {
                  const exists = prev.some(s => s.id === editSessionRes.data.id);
                  return exists ? prev : [...prev, editSessionRes.data];
                });
                setPracticeSession(editSessionRes.data);
              }
            } catch (e) {
              // セッションが見つからなくても編集は許可
            }
          }

          const opponentId = match.player1Id === currentPlayer.id ? match.player2Id : match.player1Id;
          setFormData({
            matchDate: match.matchDate,
            opponentName: match.opponentName,
            opponentId: opponentId || null,
            result: match.result,
            scoreDifference: match.scoreDifference,
            isLesson: match.isLesson === true,
            matchNumber: match.matchNumber,
            personalNotes: match.myPersonalNotes || '',
            otetsukiCount: match.myOtetsukiCount ?? null,
          });
          setOriginalIsLesson(match.isLesson === true);
          setInitialLoading(false);
        }

        // 練習日がない場合、または編集モードの場合はここでローディング終了
        if (!sessionRes.data || isEdit) {
          setInitialLoading(false);
        }
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
        setInitialLoading(false);
      }
    };

    fetchData();
  }, [id, isEdit, currentPlayer]);

  // useEffect 2: 練習セッション詳細取得 + 既存試合/組み合わせチェック（初回のみ）
  useEffect(() => {
    const fetchSessionDetails = async () => {
      if (!practiceSession || isEdit) return;

      try {
        // 全ペアリングを先に取得（IDベースで参加試合番号を特定するため）
        let defaultMatchNumber = formData.matchNumber;
        const newMatchDataCache = {};
        const newPairingCache = {};

        const allPairingsRes = await pairingAPI.getByDate(formData.matchDate).catch(() => ({ data: [] }));
        const allPairings = allPairingsRes.data || [];
        allPairingsCache.current = allPairings;

        // 全試合番号をタブ表示用にセット
        const allMatchNumbers = Array.from({ length: practiceSession.totalMatches || 1 }, (_, i) => i + 1);
        setParticipatingMatchNumbers(allMatchNumbers);

        // ペアリングを試合番号ごとにキャッシュ
        allMatchNumbers.forEach(num => {
          const matchPairings = allPairings.filter(p => p.matchNumber === num);
          const myPairing = matchPairings.find(
            p => p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id
          );
          newPairingCache[num] = myPairing || null;
        });

        // 全試合の既存記録 + 抜け番活動を一括取得
        const [matchResults, byeActivitiesRes] = await Promise.all([
          Promise.all(allMatchNumbers.map(num =>
            matchAPI.getByPlayerDateAndMatchNumber(currentPlayer.id, formData.matchDate, num)
              .then(res => ({ matchNumber: num, exists: true, data: res.data }))
              .catch(() => ({ matchNumber: num, exists: false }))
          )),
          byeActivityAPI.getByDate(formData.matchDate).catch(err => {
            console.warn('抜け番活動の取得に失敗:', err);
            return { data: [] };
          }),
        ]);

        // 既存記録をキャッシュ
        matchResults.forEach(r => { newMatchDataCache[r.matchNumber] = r; });

        // 抜け番活動をキャッシュ
        const newByeCache = {};
        (byeActivitiesRes.data || []).forEach(a => {
          if (a.playerId === currentPlayer.id) {
            newByeCache[a.matchNumber] = a;
          }
        });

        // refにキャッシュ保存
        matchDataCache.current = newMatchDataCache;
        pairingCache.current = newPairingCache;
        byeActivityCache.current = newByeCache;

        // デフォルト試合番号を決定
        // 遷移元で試合番号が明示指定されている場合はそれを優先し、
        // 未指定の場合のみ未記録の試合を優先表示する
        if (!hasExplicitMatchNumber) {
          const unrecordedMatch = matchResults.find(result => !result.exists);
          defaultMatchNumber = unrecordedMatch ? unrecordedMatch.matchNumber : allMatchNumbers[0];
        }

        // デフォルト試合番号のデータを適用
        applyMatchData(defaultMatchNumber, newMatchDataCache, newPairingCache);

        initialLoadDone.current = true;
      } catch {
        setParticipatingMatchNumbers([]);
      } finally {
        setInitialLoading(false);
      }
    };

    fetchSessionDetails();
  }, [practiceSession, currentPlayer.id, currentPlayer.name, isEdit]);

  // 活動種別の定義
  const ACTIVITY_TYPES = [
    { value: 'READING', label: '読み', icon: BookOpen },
    { value: 'SOLO_PICK', label: '一人取り', icon: User },
    { value: 'OBSERVING', label: '見学', icon: Eye },
    { value: 'ASSIST_OBSERVING', label: '見学対応', icon: UsersRound },
    { value: 'OTHER', label: 'その他', icon: MoreHorizontal },
    { value: 'ABSENT', label: '休み', icon: UserX },
  ];

  // 試合番号のデータをstateに適用する共通関数
  const applyMatchData = (matchNumber, matchCache, pairCache) => {
    // 手動抜け番モードは「その試合でプルダウンから抜け番を選んだ」瞬間のみ有効な UI 状態。
    // 別試合のデータを適用するときは必ず解除し、抜け番モードが別試合に持ち越されないようにする
    // （抜け番判定は下の分岐で isByeMatch として再計算される）。applyMatchData は
    // 初期ロード・タブ/スワイプ切替時のみ呼ばれ、抜け番選択時には呼ばれない。
    setManualByeMode(false);

    const existingResult = matchCache[matchNumber];
    if (existingResult && existingResult.exists) {
      const match = existingResult.data;
      setIsExistingMatch(true);
      setIsByeMatch(false);
      setExistingByeActivity(null);
      const opponentId = match.player1Id === currentPlayer.id ? match.player2Id : match.player1Id;
      setFormData(prev => ({
        ...prev,
        matchNumber: matchNumber,
        opponentName: match.opponentName,
        opponentId: opponentId || null,
        result: match.result,
        scoreDifference: match.scoreDifference != null ? Number(match.scoreDifference) : 0,
        isLesson: match.isLesson === true,
        personalNotes: match.myPersonalNotes || '',
        otetsukiCount: match.myOtetsukiCount ?? null,
      }));
    } else {
      setIsExistingMatch(false);
      const myPairing = pairCache[matchNumber];

      // 抜け番判定: 自分のペアリングがない + その試合に他のペアリングが存在する
      const matchPairings = allPairingsCache.current.filter(p => p.matchNumber === matchNumber);
      const isBye = !myPairing && matchPairings.length > 0;

      if (isBye) {
        setIsByeMatch(true);
        // 既存の抜け番活動を読み込む
        const existingBye = byeActivityCache.current[matchNumber];
        if (existingBye) {
          setExistingByeActivity(existingBye);
          setByeActivityType(existingBye.activityType);
          setByeFreeText(existingBye.freeText || '');
        } else {
          setExistingByeActivity(null);
          setByeActivityType('');
          setByeFreeText('');
        }
        setFormData(prev => ({
          ...prev,
          matchNumber: matchNumber,
          opponentId: null,
          opponentName: '',
        }));
      } else if (myPairing) {
        setIsByeMatch(false);
        setExistingByeActivity(null);
        const opponentId = myPairing.player1Id === currentPlayer.id
          ? myPairing.player2Id
          : myPairing.player1Id;
        const opponentName = myPairing.player1Id === currentPlayer.id
          ? myPairing.player2Name
          : myPairing.player1Name;
        setFormData(prev => ({
          ...prev,
          matchNumber: matchNumber,
          opponentId: opponentId,
          opponentName: opponentName,
          result: '勝ち',
          scoreDifference: 0,
          isLesson: false,
          personalNotes: '',
          otetsukiCount: null,
        }));
      } else {
        setIsByeMatch(false);
        setExistingByeActivity(null);
        setFormData(prev => ({
          ...prev,
          matchNumber: matchNumber,
          opponentId: null,
          opponentName: '',
          result: '勝ち',
          scoreDifference: 0,
          isLesson: false,
          personalNotes: '',
          otetsukiCount: null,
        }));
      }
    }
  };

  // useEffect 3: タブ切り替え時（キャッシュから即座に反映）
  useEffect(() => {
    if (!initialLoadDone.current) return;
    applyMatchData(formData.matchNumber, matchDataCache.current, pairingCache.current);
  }, [formData.matchNumber]);

  // 試合番号スワイプ移動 ---------------------------------------------------------

  // タブに表示する試合番号の配列（参加試合番号 or 全試合番号）
  const getTabMatchNumbers = () =>
    participatingMatchNumbers.length > 0
      ? participatingMatchNumbers
      : (practiceSession ? Array.from({ length: practiceSession.totalMatches }, (_, i) => i + 1) : []);

  // ユーザー操作による入力変更を dirty として記録（新規入力フローのみ。applyMatchData では立てない）
  const markDirty = () => {
    if (!isEdit) setIsDirty(true);
  };

  // 試合番号を実際に切り替える（確認後 or dirtyでないとき）
  const performMatchNumberChange = (num, fromSwipe) => {
    if (fromSwipe) {
      setSlide(prev => ({ dir: num > formData.matchNumber ? 'next' : 'prev', key: prev.key + 1 }));
    }
    setFormData(prev => ({ ...prev, matchNumber: num }));
    setIsDirty(false); // 切替後は対象試合のデータを読み込むため未変更状態に戻す
  };

  // 試合番号変更の共通ガード（タブタップ・スワイプ確定の両経路が通る）
  const requestMatchNumberChange = (num, { fromSwipe = false } = {}) => {
    if (num === formData.matchNumber) return;
    const total = getTabMatchNumbers().length;
    if (num < 1 || num > total) return; // 端を越える移動は無効（端で止まる）
    // 編集モードはタブ切替自体が現状機能しないため対象外（従来動作を維持）
    if (isEdit) {
      setFormData(prev => ({ ...prev, matchNumber: num }));
      return;
    }
    if (isDirty) {
      setPendingChange({ num, fromSwipe });
      setShowSwitchConfirm(true);
    } else {
      performMatchNumberChange(num, fromSwipe);
    }
  };

  const confirmSwitch = () => {
    if (pendingChange) performMatchNumberChange(pendingChange.num, pendingChange.fromSwipe);
    setPendingChange(null);
    setShowSwitchConfirm(false);
  };

  const cancelSwitch = () => {
    setPendingChange(null);
    setShowSwitchConfirm(false);
  };

  // スワイプ検出（指追従なし。離した時点の移動量で前後の試合へ切替）
  const handleContentTouchStart = (e) => {
    if (isEdit || getTabMatchNumbers().length <= 1) return;
    // 共通ヘッダー等（data-swipe-ignore 付き要素）から始まったタッチはスワイプ対象外
    if (e.target instanceof Element && e.target.closest('[data-swipe-ignore]')) return;
    const t = e.touches[0];
    swipeStart.current = { x: t.clientX, y: t.clientY };
  };

  const handleContentTouchEnd = (e) => {
    const s = swipeStart.current;
    swipeStart.current = null;
    if (!s) return;
    const t = e.changedTouches[0];
    const dx = t.clientX - s.x;
    const dy = t.clientY - s.y;
    if (!isHorizontalSwipe(dx, dy)) return; // 縦スクロール・タップは無視
    const width = contentRef.current?.offsetWidth || window.innerWidth || 0;
    const dir = resolveSwipe({ dx, containerWidth: width });
    if (!dir) return;
    const target = dir === 'next' ? formData.matchNumber + 1 : formData.matchNumber - 1;
    requestMatchNumberChange(target, { fromSwipe: true });
  };

  // スワイプ確定時のスライドインアニメーション（約0.2秒）
  useEffect(() => {
    if (!slide.dir) return;
    const from = slide.dir === 'next' ? '100%' : '-100%';
    setSlideStyle({ transform: `translateX(${from})` });
    let raf2 = 0;
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        setSlideStyle({ transform: 'translateX(0)', transition: 'transform 0.2s ease-out' });
      });
    });
    const timer = window.setTimeout(() => setSlideStyle({}), 260);
    return () => {
      cancelAnimationFrame(raf1);
      if (raf2) cancelAnimationFrame(raf2);
      clearTimeout(timer);
    };
  }, [slide]);

  // 試合番号が変わったら、アクティブタブを画面内へ自動スクロール
  useEffect(() => {
    scrollActiveTabIntoView(tabBarRef.current);
  }, [formData.matchNumber]);

  // 参加登録を自動実行
  const handleAutoRegister = async () => {
    if (!practiceSession) return;
    setIsRegistering(true);
    try {
      const now = new Date();
      const year = now.getFullYear();
      const month = now.getMonth() + 1;

      // 既存の月間参加登録を取得
      const existingRes = await practiceAPI.getPlayerParticipations(currentPlayer.id, year, month);
      const existing = existingRes.data || {};

      // 今日のセッションの全試合番号を追加
      const totalMatches = practiceSession.totalMatches || 1;
      const todayMatchNumbers = Array.from({ length: totalMatches }, (_, i) => i + 1);
      const existingForSession = existing[practiceSession.id] || [];
      const merged = [...new Set([...existingForSession, ...todayMatchNumbers])];
      existing[practiceSession.id] = merged;

      // 全参加データを構築して保存
      const participationsList = [];
      Object.entries(existing).forEach(([sessionId, matchNumbers]) => {
        matchNumbers.forEach((matchNumber) => {
          participationsList.push({
            sessionId: Number(sessionId),
            matchNumber: matchNumber,
          });
        });
      });

      await practiceAPI.registerParticipations({
        playerId: currentPlayer.id,
        year,
        month,
        participations: participationsList,
      });

      setShowParticipationDialog(false);
    } catch (err) {
      console.error('参加登録エラー:', err);
      setError('参加登録に失敗しました');
      setShowParticipationDialog(false);
    } finally {
      setIsRegistering(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    markDirty();
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // 枚数差ピッカー（末尾の「指導」選択時は指導試合フラグを立てる）
  const handleScoreChange = (e) => {
    const value = e.target.value;
    markDirty();
    if (value === 'lesson') {
      setFormData((prev) => ({ ...prev, isLesson: true }));
    } else {
      setFormData((prev) => ({ ...prev, isLesson: false, scoreDifference: value }));
    }
  };

  // 対戦相手プルダウン: 当日参加者＋「抜け番」。__bye__ 選択で抜け番モードへ遷移
  const handleOpponentSelect = (e) => {
    const val = e.target.value;
    markDirty();
    if (val === '__bye__') {
      setManualByeMode(true);
      // 既存の抜け番活動を読み込む
      const existingBye = byeActivityCache.current[formData.matchNumber];
      if (existingBye) {
        setExistingByeActivity(existingBye);
        setByeActivityType(existingBye.activityType);
        setByeFreeText(existingBye.freeText || '');
      } else {
        setExistingByeActivity(null);
        setByeActivityType('');
        setByeFreeText('');
      }
      return;
    }
    if (!val) return;
    const id = parseInt(val);
    const sel = (practiceSession?.participants || []).find((p) => p.id === id)
      || players.find((p) => p.id === id);
    if (sel) {
      setFormData((prev) => ({ ...prev, opponentId: sel.id, opponentName: sel.name }));
    }
  };

  // 確定済みの相手をタップ → プルダウンに戻して変更可能にする
  const handleChangeOpponent = () => {
    markDirty();
    setFormData((prev) => ({ ...prev, opponentId: null, opponentName: '' }));
  };

  // 未参加の選手を検索モーダルから選択（保存時にサーバ側で自動参加登録される）
  const handlePickNonParticipant = (player) => {
    markDirty();
    setFormData((prev) => ({ ...prev, opponentId: player.id, opponentName: player.name }));
    setShowSearchModal(false);
    setSearchTerm('');
  };

  const handleByeActivitySubmit = async () => {
    if (!byeActivityType) {
      setError('活動内容を選択してください');
      return;
    }
    if (byeActivityType === 'OTHER' && !byeFreeText.trim()) {
      setError('その他の場合は内容を入力してください');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const data = {
        sessionDate: formData.matchDate,
        matchNumber: parseInt(formData.matchNumber),
        playerId: currentPlayer.id,
        activityType: byeActivityType,
        freeText: byeActivityType === 'OTHER' ? byeFreeText.trim() : null,
      };

      if (existingByeActivity) {
        await byeActivityAPI.update(existingByeActivity.id, {
          activityType: byeActivityType,
          freeText: byeActivityType === 'OTHER' ? byeFreeText.trim() : null,
        });
      } else {
        await byeActivityAPI.create(data);
      }

      navigate('/');
    } catch (err) {
      console.error('抜け番活動保存エラー:', err);
      setError(err.response?.data?.message || '抜け番活動の保存に失敗しました');
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!formData.opponentName) {
      setError('対戦相手を選択してください');
      return;
    }

    setLoading(true);

    try {
      if (isEdit) {
        const isLesson = formData.isLesson === true;
        // 指導試合（または指導↔通常の変換）は詳細版APIで保存する。
        // 簡易版(update)は is_lesson を扱えず scoreDifference 必須のため。
        if ((isLesson || originalIsLesson) && formData.opponentId) {
          const winnerId = formData.result === '勝ち' ? currentPlayer.id : formData.opponentId;
          await matchAPI.updateDetailed(
            id,
            winnerId,
            isLesson ? null : parseInt(formData.scoreDifference),
            currentPlayer.id,
            formData.personalNotes || null,
            formData.otetsukiCount,
            isLesson
          );
        } else {
          const submitData = {
            ...formData,
            playerId: currentPlayer.id,
            scoreDifference: parseInt(formData.scoreDifference),
            matchNumber: parseInt(formData.matchNumber),
            personalNotes: formData.personalNotes || null,
          };
          await matchAPI.update(id, submitData);
        }
        navigate('/matches');
      } else {
        if (formData.opponentId) {
          const player1Id = currentPlayer.id;
          const player2Id = formData.opponentId;
          const winnerId = formData.result === '勝ち' ? currentPlayer.id : formData.opponentId;

          const isLesson = formData.isLesson === true;
          const detailedData = {
            matchDate: formData.matchDate,
            matchNumber: parseInt(formData.matchNumber),
            player1Id: player1Id,
            player2Id: player2Id,
            winnerId: winnerId,
            // 指導試合は枚数差を持たない
            scoreDifference: isLesson ? null : parseInt(formData.scoreDifference),
            isLesson: isLesson,
            personalNotes: formData.personalNotes || null,
            otetsukiCount: formData.otetsukiCount,
            createdBy: currentPlayer.id,
            updatedBy: currentPlayer.id
          };

          await matchAPI.createDetailed(detailedData);
        } else {
          const submitData = {
            ...formData,
            playerId: currentPlayer.id,
            scoreDifference: parseInt(formData.scoreDifference),
            matchNumber: parseInt(formData.matchNumber),
          };
          await matchAPI.create(submitData);
        }

        navigate('/');
      }
    } catch (err) {
      console.error('保存エラー:', err);

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
              personalNotes: formData.personalNotes || null,
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
      setLoading(false);
    }
  };

  // 初期ローディング中
  if (initialLoading) {
    return <LoadingScreen />;
  }

  // 横スワイプで前後の試合へ移動できることの控えめな案内。
  // スワイプが効く条件（新規入力かつタブ2件以上）のときだけ表示し、誤誘導を避ける。
  const swipeHint = !isEdit && getTabMatchNumbers().length > 1 ? (
    <p className="text-center text-xs text-[#9ca3af] mb-2 select-none">
      ‹ スワイプで試合を切替 ›
    </p>
  ) : null;

  // ヘッダー: 日付（短縮）＋ 会場名（venueName 無しは日付のみ）
  const headerDate = new Date(formData.matchDate + 'T00:00:00');
  const dateShort = `${headerDate.getMonth() + 1}/${headerDate.getDate()}`;
  const weekdayShort = headerDate.toLocaleDateString('ja-JP', { weekday: 'short' });
  const venueName = practiceSession?.venueName || '';

  // 対戦相手プルダウンの母集団 = その練習セッションの「アクティブ参加者」（自分を除く）。
  // participants(PlayerDto) はステータスを持たない全参加者（CANCELLED/DECLINED 等も含む）ため、
  // matchParticipants(ステータス付き) から WON/PENDING の参加者名を集約して絞り込む。
  // 選手名は players.name の UNIQUE 制約により一意なので名前で突合できる。
  // matchParticipants が無い場合（旧データ・取得失敗）は絞り込めないため全参加者にフォールバック。
  const allParticipants = practiceSession?.participants || [];
  const activeParticipantNames = new Set();
  let hasMatchParticipantData = false;
  Object.values(practiceSession?.matchParticipants || {}).forEach((list) => {
    (list || []).forEach((mp) => {
      hasMatchParticipantData = true;
      if (mp.status === 'WON' || mp.status === 'PENDING') {
        activeParticipantNames.add(mp.name);
      }
    });
  });
  const isActiveParticipant = (p) => !hasMatchParticipantData || activeParticipantNames.has(p.name);

  const sessionParticipants = allParticipants.filter(
    (p) => p.id !== currentPlayer.id && isActiveParticipant(p)
  );
  // 検索除外用のアクティブ参加者ID集合（キャンセル等の非アクティブ選手は「未参加」扱いで検索に出す）
  const activeParticipantIdSet = new Set(
    allParticipants.filter(isActiveParticipant).map((p) => p.id)
  );

  // 確定済みの相手の級（opponentId × 全選手で突合 → "(A)"）
  const opponentPlayer = formData.opponentId
    ? players.find((p) => p.id === formData.opponentId)
    : null;
  const opponentGrade = opponentPlayer ? kyuRankShortLabel(opponentPlayer.kyuRank) : '';

  // 対戦相手を変更させない条件:
  //  - 編集モード（更新APIは対戦者IDを変えない）
  //  - 入力済み試合（相手を変えると別ペアの新規試合が作られ、旧試合が残って二重登録になる）
  // どちらも「保存で同じ試合を上書き」する前提のため、相手変更UIは出さず読み取り専用にする。
  const opponentReadOnly = isEdit || isExistingMatch;

  // 「未参加から検索」母集団 = 全選手 − 当日のアクティブ参加者 − 自分（players は既に自分を除外済み）
  const searchLower = searchTerm.trim().toLowerCase();
  const nonParticipants = players
    .filter((p) => !activeParticipantIdSet.has(p.id))
    .filter((p) => !searchLower || (p.name || '').toLowerCase().includes(searchLower));

  return (
    <div
      className="min-h-screen bg-[#f2ede6] pb-16 overflow-hidden"
      onTouchStart={handleContentTouchStart}
      onTouchEnd={handleContentTouchEnd}
    >
      {/* ナビゲーションバー */}
      <div data-swipe-ignore className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付＋会場表示 */}
          <div className="flex items-center justify-center py-2.5">
            <span className="text-sm font-semibold text-white">
              <span className="font-bold tracking-wide">{dateShort}</span>({weekdayShort})
              {venueName && <span className="ml-3">{venueName}</span>}
            </span>
          </div>

          {/* 試合番号タブ */}
          {practiceSession && (
            <div ref={tabBarRef} className="flex overflow-x-auto -mb-px">
              {getTabMatchNumbers().map((num) => (
                <button
                  key={num}
                  type="button"
                  data-active={formData.matchNumber === num}
                  onClick={() => requestMatchNumberChange(num)}
                  className={`flex-shrink-0 px-4 py-2 text-sm font-medium transition-colors border-b-2 ${
                    formData.matchNumber === num
                      ? 'border-white text-white'
                      : 'border-transparent text-white/60 hover:text-white hover:border-white/50'
                  }`}
                >
                  {num}試合目
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* スワイプで前後の試合へ移動できるコンテンツ領域（確定時はスライドイン。編集モードは無効） */}
      <div
        ref={contentRef}
        data-testid="matchform-swipe-area"
        style={slideStyle}
      >
      {/* 今日が練習日でない場合はフォームを表示せずブロック */}
      {!isEdit && practiceSessions.length === 0 ? (
        <div className="h-full px-6 overflow-hidden pt-28 flex items-start justify-center">
          <div className="p-6 bg-[#f9f6f2] rounded-lg shadow-sm text-center max-w-sm w-full mt-8">
            <AlertCircle className="w-10 h-10 text-[#6b7280] mx-auto mb-3" />
            <h3 className="font-bold text-[#374151] mb-2">今日は練習日ではありません</h3>
            <p className="text-sm text-[#6b7280] mb-4">
              練習日を登録してから試合記録を入力してください。
            </p>
            <div className="space-y-2">
              <Link
                to="/practice"
                className="block w-full bg-[#4a6b5a] text-white py-2.5 px-4 rounded-lg hover:bg-[#3d5a4c] transition-colors font-medium text-sm"
              >
                練習日を確認する
              </Link>
              <button
                type="button"
                onClick={() => navigate(-1)}
                className="block w-full text-[#6b7280] py-2 px-4 rounded-lg hover:bg-[#e5ebe7] transition-colors text-sm"
              >
                戻る
              </button>
            </div>
          </div>
        </div>
      ) : (
      (isByeMatch || manualByeMode) && !isEdit ? (
        <div className="px-[22px] pt-28 pb-8">
          {swipeHint}
          <div className="mf-notice warn">
            <span className="dot" />この試合（{formData.matchNumber}試合目）は抜け番です
          </div>

          {manualByeMode && !isByeMatch && (
            <button
              type="button"
              onClick={() => {
                markDirty();
                setManualByeMode(false);
                setByeActivityType('');
                setByeFreeText('');
                setExistingByeActivity(null);
              }}
              className="text-xs text-[#8a8275] underline underline-offset-2 hover:text-[#5b5446] mb-4"
            >
              通常入力に戻る
            </button>
          )}

          {existingByeActivity && (
            <div className="mf-notice info">
              <span className="dot" />入力済みです。保存で上書きされます。
            </div>
          )}

          <span className="mf-label">活動内容</span>
          <div className="mf-acts">
            {ACTIVITY_TYPES.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                onClick={() => { markDirty(); setByeActivityType(value); }}
                className={`mf-act${byeActivityType === value ? ' active' : ''}`}
              >
                <span className="ic"><Icon size={20} /></span>
                <span className="lab">{label}</span>
                <span className="check"><Check size={18} /></span>
              </button>
            ))}
          </div>

          {byeActivityType === 'OTHER' && (
            <input
              type="text"
              value={byeFreeText}
              onChange={(e) => { markDirty(); setByeFreeText(e.target.value); }}
              placeholder="活動内容を入力…"
              className="mf-memo-line mt-3"
            />
          )}

          {error && (
            <div className="mf-notice warn">
              <span className="dot" />{error}
            </div>
          )}

          <div className="mf-actions">
            <button type="button" onClick={() => navigate('/matches')} className="mf-cancel">
              キャンセル
            </button>
            <button
              type="button"
              onClick={handleByeActivitySubmit}
              disabled={loading}
              className="mf-submit"
            >
              {loading ? '保存中...' : existingByeActivity ? '更新する' : '登録'}
            </button>
          </div>
        </div>
      ) : (
      <form onSubmit={handleSubmit} className="px-[22px] pt-28 pb-8">
        {swipeHint}

        {/* 既存試合の警告メッセージ */}
        {!isEdit && isExistingMatch && (
          <div className="mf-notice info">
            <span className="dot" />入力済みの試合です。保存で上書きされます。
          </div>
        )}

        {/* 対戦相手（主題） */}
        {practiceSession && (
          opponentReadOnly ? (
            /* 編集モード・入力済み試合は対戦相手の変更を出さず読み取り専用表示（相手変更は二重登録/不整合になるため） */
            <div className="mf-subject">
              <span className="mf-vs">vs</span>
              <span className="mf-opp-name" style={{ cursor: 'default' }}>
                {formData.opponentName || '—'}
              </span>
              {opponentGrade && <span className="mf-grade">{opponentGrade}</span>}
            </div>
          ) : formData.opponentId ? (
            <div className="mf-subject">
              <span className="mf-vs">vs</span>
              <button type="button" className="mf-opp-name" onClick={handleChangeOpponent}>
                {formData.opponentName}
              </button>
              {opponentGrade && <span className="mf-grade">{opponentGrade}</span>}
              <button
                type="button"
                className="mf-opp-chev"
                aria-label="対戦相手を変更"
                onClick={handleChangeOpponent}
              >
                <ChevronDown size={22} />
              </button>
              <button
                type="button"
                className="mf-search-other"
                aria-label="未参加の選手から検索"
                onClick={() => setShowSearchModal(true)}
              >
                <Search size={16} />
              </button>
            </div>
          ) : (
            <div className="mf-subject mf-subject--pick">
              <span className="mf-vs">vs</span>
              <span className="mf-oppselect">
                <select
                  aria-label="対戦相手"
                  value=""
                  onChange={handleOpponentSelect}
                  className="placeholder"
                >
                  <option value="">対戦相手を選択</option>
                  {sessionParticipants.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}{p.kyuRank ? `（${p.kyuRank.charAt(0)}）` : ''}
                    </option>
                  ))}
                  <option value="__bye__">抜け番</option>
                </select>
                <ChevronDown className="chev" size={18} />
              </span>
              <button
                type="button"
                className="mf-search-other"
                aria-label="未参加の選手から検索"
                onClick={() => setShowSearchModal(true)}
              >
                <Search size={16} />
              </button>
            </div>
          )
        )}

        {/* 結果・枚数差・お手付き（横並び） */}
        <div className="mf-controls">
          <div className="mf-field">
            <span className="mf-label">結果</span>
            <div className="mf-toggle">
              {['勝ち', '負け'].map((result) => (
                <button
                  key={result}
                  type="button"
                  onClick={() => {
                    markDirty();
                    setFormData((prev) => ({ ...prev, result }));
                  }}
                  className={`opt ${result === '勝ち' ? 'win' : 'lose'}${formData.result === result ? ' active' : ''}`}
                >
                  {result}
                </button>
              ))}
            </div>
          </div>

          <div className="mf-field">
            <span className="mf-label">枚数差</span>
            <div className="mf-picker">
              <select
                name="scoreDifference"
                aria-label="枚数差"
                value={formData.isLesson ? 'lesson' : formData.scoreDifference}
                onChange={handleScoreChange}
                className={formData.isLesson ? 'lesson' : ''}
                required
              >
                {Array.from({ length: 26 }, (_, i) => i).map((num) => (
                  <option key={num} value={num}>{num}</option>
                ))}
                {/* 登録済み相手との試合のみ「指導」を選択可能（簡易入力フローは対象外） */}
                {formData.opponentId && <option value="lesson">指導</option>}
              </select>
              {!formData.isLesson && <span className="mf-unit">枚</span>}
              <ChevronDown className="chev" size={16} />
            </div>
          </div>

          <div className="mf-field">
            <span className="mf-label">お手付き</span>
            <div className="mf-picker">
              <select
                name="otetsukiCount"
                aria-label="お手付き回数"
                value={formData.otetsukiCount ?? ''}
                onChange={(e) => {
                  markDirty();
                  setFormData(prev => ({
                    ...prev,
                    otetsukiCount: e.target.value === '' ? null : parseInt(e.target.value)
                  }));
                }}
              >
                <option value="">不明</option>
                {Array.from({ length: 21 }, (_, i) => i).map((num) => (
                  <option key={num} value={num}>{num}</option>
                ))}
              </select>
              {formData.otetsukiCount != null && <span className="mf-unit">回</span>}
              <ChevronDown className="chev" size={16} />
            </div>
          </div>
        </div>
        {formData.isLesson && <div className="mf-lesson-hint">指導試合＝枚数差なし</div>}

        {/* メモ */}
        <div className="mf-memo">
          <span className="mf-label">メモ</span>
          <textarea
            name="personalNotes"
            value={formData.personalNotes}
            onChange={(e) => {
              handleChange(e);
              e.target.style.height = 'auto';
              e.target.style.height = e.target.scrollHeight + 'px';
            }}
            ref={(el) => {
              if (el) {
                el.style.height = 'auto';
                el.style.height = el.scrollHeight + 'px';
              }
            }}
            rows="2"
            placeholder="試合の感想、反省点など…"
            className="mf-memo-line"
          ></textarea>
        </div>

        {/* エラー表示 */}
        {error && !isEdit && (
          <div className="mf-notice warn">
            <span className="dot" />{error}
          </div>
        )}

        {/* アクション */}
        {practiceSessions.length > 0 && (
          <div className="mf-actions">
            <button type="button" onClick={() => navigate('/matches')} className="mf-cancel">
              キャンセル
            </button>
            <button type="submit" disabled={loading} className="mf-submit">
              {loading ? '保存中...' : isEdit ? '更新する' : '登録'}
            </button>
          </div>
        )}
      </form>
      )
      )}
      </div>

      {/* 試合切替の確認ダイアログ（入力途中の警告） */}
      {showSwitchConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-xl max-w-sm w-full p-6 shadow-xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-yellow-100 flex items-center justify-center">
                <AlertCircle className="w-5 h-5 text-yellow-600" />
              </div>
              <h3 className="text-lg font-semibold text-[#374151]">確認</h3>
            </div>
            <p className="text-sm text-[#6b7280] mb-6">
              入力中の内容は破棄されます。移動しますか？
            </p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={cancelSwitch}
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-[#6b7280] hover:bg-gray-50 transition-colors text-sm font-medium"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={confirmSwitch}
                className="flex-1 px-4 py-2.5 bg-[#1A3654] text-white rounded-lg hover:bg-[#122740] transition-colors text-sm font-medium"
              >
                移動する
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 参加未登録ダイアログ */}
      {showParticipationDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-xl max-w-sm w-full p-6 shadow-xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-yellow-100 flex items-center justify-center">
                <UserPlus className="w-5 h-5 text-yellow-600" />
              </div>
              <h3 className="text-lg font-semibold text-[#374151]">参加未登録</h3>
            </div>
            <p className="text-sm text-[#6b7280] mb-6">
              本日の練習に参加登録されていません。参加登録を行いますか？
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => { setShowParticipationDialog(false); navigate('/'); }}
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-[#6b7280] hover:bg-gray-50 transition-colors text-sm font-medium"
              >
                戻る
              </button>
              <button
                onClick={handleAutoRegister}
                disabled={isRegistering}
                className="flex-1 px-4 py-2.5 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:opacity-50 text-sm font-medium"
              >
                {isRegistering ? '登録中...' : '登録して入力する'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 未参加の選手から検索（簡易インクリメンタル検索） */}
      {showSearchModal && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4"
          onClick={() => { setShowSearchModal(false); setSearchTerm(''); }}
        >
          <div
            className="bg-[#fffdf9] rounded-xl max-w-sm w-full p-5 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-base font-bold text-[#1A2744] mb-3">未参加の選手から検索</h3>
            <input
              autoFocus
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="名前で検索…"
              className="w-full px-0 py-2 mb-2 border-0 border-b border-[#d0c5b8] bg-transparent focus:ring-0 focus:border-[#82655a] text-[#1A2744] placeholder-[#ada697]"
            />
            <div className="mf-search-list">
              {nonParticipants.length === 0 ? (
                <p className="text-sm text-[#9a9183] py-4 text-center">
                  {searchTerm ? '該当する選手がいません' : '未参加の選手がいません'}
                </p>
              ) : (
                nonParticipants.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    className="mf-search-row"
                    onClick={() => handlePickNonParticipant(p)}
                  >
                    <span>{p.name}</span>
                    {kyuRankShortLabel(p.kyuRank) && (
                      <span className="grade">{kyuRankShortLabel(p.kyuRank)}</span>
                    )}
                  </button>
                ))
              )}
            </div>
            <button
              type="button"
              onClick={() => { setShowSearchModal(false); setSearchTerm(''); }}
              className="mt-3 w-full text-center text-sm text-[#8a8275]"
            >
              閉じる
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default MatchForm;
