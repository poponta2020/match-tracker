import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { homeAPI } from '../api';
import LoadingScreen from '../components/LoadingScreen';
import washiTile from '../assets/washi-fiber-tile.png';

// 明朝ディスプレイ（システム内蔵フォント。web フォント新規導入なし）
const MINCHO = "'Hiragino Mincho ProN','Yu Mincho','Noto Serif JP',serif";

// 確定参加者（キャンセル待ち・辞退・キャンセルを除く）判定。現行 Home の表示フィルタと同一。
const isActiveParticipant = (p) =>
  p.status !== 'WAITLISTED' &&
  p.status !== 'WAITLIST_DECLINED' &&
  p.status !== 'CANCELLED' &&
  p.status !== 'DECLINED';

const Home = () => {
  const { currentPlayer } = useAuth();
  const [loading, setLoading] = useState(true);
  const [nextPractice, setNextPractice] = useState(null);
  const [participationGroups, setParticipationGroups] = useState([]);

  const fetchData = useCallback(async (signal) => {
    if (!currentPlayer?.id) return;
    try {
      const res = await homeAPI.getData(currentPlayer.id);

      // リクエストがキャンセルされた場合は状態更新をスキップ
      if (signal?.aborted) return;

      const data = res.data;

      // 参加率グループ（団体別）
      setParticipationGroups(data.participationGroups || []);

      // 次の練習情報（参加者リストも含まれている）
      setNextPractice(data.nextPractice || null);
    } catch (error) {
      if (error.name !== 'AbortError') {
        console.error('データ取得エラー:', error);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, [currentPlayer]);

  // 初回データ取得
  useEffect(() => {
    const abortController = new AbortController();
    if (currentPlayer?.id) {
      fetchData(abortController.signal);
    }
    return () => {
      abortController.abort();
    };
  }, [currentPlayer, fetchData]);

  // フォーカス復帰時のリフレッシュ（初回マウント直後は無視）
  useEffect(() => {
    const mountedAt = Date.now();
    let abortController = null;
    const handleFocus = () => {
      // マウントから2秒以内のfocusは無視（初回ロードとの重複防止）
      if (Date.now() - mountedAt < 2000) return;
      if (currentPlayer?.id) {
        // 前回のリクエストをキャンセル
        if (abortController) {
          abortController.abort();
        }
        abortController = new AbortController();
        setLoading(true);
        fetchData(abortController.signal);
      }
    };
    window.addEventListener('focus', handleFocus);
    return () => {
      window.removeEventListener('focus', handleFocus);
      if (abortController) {
        abortController.abort();
      }
    };
  }, [currentPlayer, fetchData]);

  const formatTime = (time) => {
    if (!time) return '';
    return time.substring(0, 5); // "HH:MM:SS" -> "HH:MM"
  };

  const now = new Date();
  const monthLabel = `${now.getMonth() + 1}月`;

  if (loading) {
    return <LoadingScreen />;
  }

  // ヒーロー用の派生値
  const activeCount = (nextPractice?.participants || []).filter(isActiveParticipant).length;
  const registered = nextPractice?.registered === true;
  const matchNumbers = nextPractice?.matchNumbers || [];
  // 今日 or 登録済み → 対戦確認画面へ / それ以外（未登録）→ 参加登録
  const goToPairings = nextPractice && (nextPractice.today || registered);
  const ctaLabel = goToPairings ? '対戦確認画面へ' : '参加登録';
  const ctaTo = goToPairings
    ? `/pairings?date=${nextPractice?.sessionDate}`
    : '/practice/participation';

  return (
    // 本文の"地" = 和紙繊維テクスチャ（決定論的な静的タイルを repeat）。
    // トップバー撤去（design-spec §2.5）: -mt-16 で最上部まで引き上げ、Layout の
    // 固定フォールバック緑バー(#4a6b5a・z-40)を relative z-40 で覆い、深緑ヒーローが
    // 最上部を占有する（緑シェルは下部5タブのみ）。画面下端まで全幅で覆う。
    <div
      className="relative z-40 -mt-16 -mx-4 sm:-mx-6 lg:-mx-8 -mb-8 min-h-screen"
      style={{ background: `#f2ede6 url(${washiTile}) repeat` }}
    >
      {/* 次の練習ヒーロー（深緑・常時表示・単一デザイン） */}
      <section
        className="bg-[#33503f] text-white"
        style={{
          backgroundImage:
            'repeating-linear-gradient(45deg, rgba(255,255,255,.032) 0 2px, transparent 2px 7px)',
        }}
      >
        {nextPractice ? (
          <>
            <div className="px-5 pt-5 pb-4">
              {/* eyebrow: TODAY/NEXT ｜ 参加予定・人数 */}
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[11px] font-bold tracking-[0.22em] text-[#e8d9c5]">
                  {nextPractice.today ? 'TODAY' : 'NEXT'}
                </span>
                <span className="text-[11px] text-white/70 text-right">
                  {registered && matchNumbers.length > 0
                    ? `${matchNumbers.join('、')}試合目に参加予定 ・ ${activeCount}名`
                    : `${activeCount}名`}
                </span>
              </div>

              {/* 主表示: 大きな明朝の日付 ｜ 会場・時刻 */}
              <div className="mt-2 flex items-end justify-between gap-3">
                <div className="flex items-baseline gap-2 min-w-0">
                  <span style={{ fontFamily: MINCHO }} className="text-[2.75rem] leading-none">
                    {(() => {
                      const d = new Date(nextPractice.sessionDate);
                      return `${d.getMonth() + 1}/${d.getDate()}`;
                    })()}
                  </span>
                  <span style={{ fontFamily: MINCHO }} className="text-sm text-white/80">
                    （{new Date(nextPractice.sessionDate).toLocaleDateString('ja-JP', { weekday: 'short' })}）
                  </span>
                </div>
                <div className="text-right shrink-0">
                  {nextPractice.venueName && (
                    <div className="text-sm text-white/85">{nextPractice.venueName}</div>
                  )}
                  {nextPractice.startTime && (
                    <div style={{ fontFamily: MINCHO }} className="text-sm text-white/70">
                      {formatTime(nextPractice.startTime)} — {formatTime(nextPractice.endTime)}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* CTA 行（上辺ヘアライン・全幅タップ） */}
            <Link
              to={ctaTo}
              className="flex items-center justify-between px-5 py-3 border-t border-white/[0.16] text-[#e8d9c5] active:bg-white/5 transition-colors"
            >
              <span className="text-sm font-medium">{ctaLabel}</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </>
        ) : (
          <div className="px-5 py-6">
            <span className="text-[11px] font-bold tracking-[0.22em] text-[#e8d9c5]">NEXT</span>
            <p className="mt-2 text-sm text-white/70">次の練習の予定はまだありません</p>
          </div>
        )}
      </section>

      {/* 参加率（カードでなく行の背景バーで表現） */}
      {participationGroups.length > 0 && (
        <div className="px-4 sm:px-6 lg:px-8 pt-6 pb-4">
          {/* 月見出し（全体で1回） */}
          <div className="mb-4">
            <h2
              style={{ fontFamily: MINCHO }}
              className="text-lg text-[#1A3654] flex items-baseline gap-2"
            >
              <span className="font-bold">{monthLabel}の参加率</span>
              <span className="font-sans text-[11px] tracking-wider text-[#8a7568]">TOP 3</span>
            </h2>
            <div className="mt-1 h-0.5 w-[34px] bg-[#5f3a2d]" />
          </div>

          {participationGroups.map((group) => {
            const top3 = group.top3 || [];
            if (top3.length === 0) return null;
            const showLabel = participationGroups.length > 1;
            const total = top3[0].totalScheduledMatches;
            const myRate = group.myRate;
            const myInTop3 = top3.some((p) => p.playerId === currentPlayer?.id);
            const myPct = myRate && myRate.rate !== null ? Math.round(myRate.rate * 100) : 0;
            return (
              <div key={group.organizationId ?? 'all'} className="mb-5">
                {/* サブラベル: 団体名（明朝・見出しより一回り小さく・目立たせる）＋総試合数（taupe小・据え置き）。1団体所属時は団体名を出さない */}
                <div className="mb-1.5">
                  {showLabel && (
                    <span style={{ fontFamily: MINCHO }} className="text-base text-[#1A3654] mr-0.5">
                      {group.organizationName}
                    </span>
                  )}
                  <span className="text-xs text-[#8a7568]">
                    {showLabel ? `（${total}試合）` : `${total}試合`}
                  </span>
                </div>

                {top3.map((player, index) => {
                  const pct = Math.round(player.rate * 100);
                  return (
                    <div
                      key={player.playerId}
                      className="flex items-center h-7 px-1 text-sm"
                      style={{
                        background: `linear-gradient(to right, rgba(26,54,84,.13) 0 ${pct}%, transparent ${pct}%)`,
                      }}
                    >
                      <span style={{ fontFamily: MINCHO }} className="w-[15px] text-[#1A3654]">
                        {index + 1}
                      </span>
                      <span className="ml-2 text-[#3d2b21] truncate">{player.playerName}</span>
                      <span className="ml-2 text-xs text-[#8a7568] shrink-0">
                        {player.participatedMatches}試合
                      </span>
                      <span style={{ fontFamily: MINCHO }} className="ml-auto text-[#1A3654]">
                        {pct}%
                      </span>
                    </div>
                  );
                })}

                {/* 自分の行（TOP3圏外のときのみ末尾に追加。茶ライン・"YOU"ラベルなし） */}
                {myRate && !myInTop3 && (
                  <div
                    className="flex items-center h-7 px-1 text-sm border-l-[3px] border-[#82655a]"
                    style={{
                      background: `linear-gradient(to right, rgba(130,101,90,.2) 0 ${myPct}%, transparent ${myPct}%)`,
                    }}
                  >
                    <span className="w-[15px]" />
                    <span className="ml-2 text-[#3d2b21] truncate">あなた</span>
                    <span className="ml-2 text-xs text-[#8a7568] shrink-0">
                      {myRate.participatedMatches}試合
                    </span>
                    {myRate.rate !== null && (
                      <span style={{ fontFamily: MINCHO }} className="ml-auto text-[#82655a]">
                        {myPct}%
                      </span>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default Home;
