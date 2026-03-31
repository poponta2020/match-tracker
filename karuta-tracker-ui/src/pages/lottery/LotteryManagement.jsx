import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { lotteryAPI } from '../../api/lottery';
import { ArrowLeft, Settings, Play, Check, Bell, BellRing } from 'lucide-react';

/**
 * 抽選管理画面（ADMIN/SUPER_ADMIN用）
 *
 * 状態遷移:
 * - idle: 初期状態（抽選実行ボタンのみ表示）
 * - preview: プレビュー表示中（確定ボタン表示）
 * - confirmed: 確定済み（通知送信ボタン表示）
 */
export default function LotteryManagement() {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();

  // デフォルト: 翌月
  const [currentDate, setCurrentDate] = useState(() => {
    const now = new Date();
    let year = now.getFullYear();
    let month = now.getMonth() + 2; // 翌月
    if (month > 12) { month = 1; year++; }
    return { year, month };
  });

  const [phase, setPhase] = useState('idle'); // idle | preview | confirmed
  const [previewResults, setPreviewResults] = useState([]);
  const [processing, setProcessing] = useState(null);
  const [error, setError] = useState(null);
  const [notifyResult, setNotifyResult] = useState(null);

  const organizationId = currentPlayer?.organizationId || null;

  const changeMonth = (delta) => {
    setCurrentDate((prev) => {
      let newMonth = prev.month + delta;
      let newYear = prev.year;
      if (newMonth > 12) { newMonth = 1; newYear++; }
      if (newMonth < 1) { newMonth = 12; newYear--; }
      return { year: newYear, month: newMonth };
    });
    // 月変更時にリセット
    setPhase('idle');
    setPreviewResults([]);
    setError(null);
    setNotifyResult(null);
  };

  // 抽選プレビュー実行
  const handlePreview = async () => {
    setProcessing('preview');
    setError(null);
    setNotifyResult(null);
    try {
      const res = await lotteryAPI.preview(currentDate.year, currentDate.month, organizationId);
      setPreviewResults(res.data);
      if (res.data.length === 0) {
        setError('対象のセッションがありません');
        setPhase('idle');
      } else {
        setPhase('preview');
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || '抽選プレビューに失敗しました';
      setError(typeof msg === 'string' ? msg : '抽選プレビューに失敗しました');
      setPhase('idle');
    } finally {
      setProcessing(null);
    }
  };

  // 抽選確定
  const handleConfirm = async () => {
    if (!confirm('抽選結果を確定しますか？\n確定するとDBに保存され、伝助への書き戻しが実行されます。')) return;

    setProcessing('confirm');
    setError(null);
    try {
      await lotteryAPI.confirm(currentDate.year, currentDate.month, organizationId);
      setPhase('confirmed');
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || '確定処理に失敗しました';
      setError(typeof msg === 'string' ? msg : '確定処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  // 全員に通知送信
  const handleNotifyAll = async () => {
    if (!confirm('全員（当選者＋キャンセル待ち）に通知を送信しますか？')) return;

    setProcessing('notifyAll');
    setError(null);
    try {
      const res = await lotteryAPI.notifyResults(currentDate.year, currentDate.month);
      setNotifyResult({ type: 'all', ...res.data });
    } catch {
      setError('通知送信に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  // キャンセル待ちのみに通知送信
  const handleNotifyWaitlisted = async () => {
    if (!confirm('キャンセル待ちの人にだけ通知を送信しますか？')) return;

    setProcessing('notifyWaitlisted');
    setError(null);
    try {
      const res = await lotteryAPI.notifyWaitlisted(currentDate.year, currentDate.month, organizationId);
      setNotifyResult({ type: 'waitlisted', ...res.data });
    } catch {
      setError('通知送信に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-4">
      {/* ヘッダー */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-1.5 rounded-lg hover:bg-gray-100">
            <ArrowLeft size={20} className="text-[#374151]" />
          </button>
          <h1 className="text-xl font-bold text-[#374151]">抽選管理</h1>
        </div>
        <button
          onClick={() => navigate('/admin/settings')}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-[#4a6b5a] border border-[#4a6b5a] rounded-lg hover:bg-[#4a6b5a] hover:text-white transition-colors"
        >
          <Settings size={14} />
          システム設定
        </button>
      </div>

      {/* 年月セレクター */}
      <div className="flex items-center justify-center gap-4 mb-6">
        <button onClick={() => changeMonth(-1)} className="p-2 rounded hover:bg-gray-100 text-[#374151]">&lt;</button>
        <span className="text-lg font-semibold text-[#374151]">{currentDate.year}年{currentDate.month}月</span>
        <button onClick={() => changeMonth(1)} className="p-2 rounded hover:bg-gray-100 text-[#374151]">&gt;</button>
      </div>

      {/* エラー表示 */}
      {error && (
        <div className="mb-4 p-3 bg-red-50 text-red-700 border border-red-200 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* 抽選実行ボタン（idle時） */}
      {phase === 'idle' && (
        <div className="flex justify-center mb-6">
          <button
            onClick={handlePreview}
            disabled={processing === 'preview'}
            className="flex items-center gap-2 px-6 py-3 bg-[#4a6b5a] hover:bg-[#3d5a4c] text-white rounded-lg font-semibold transition-colors disabled:opacity-50"
          >
            <Play size={18} />
            {processing === 'preview' ? '実行中...' : '抽選実行（プレビュー）'}
          </button>
        </div>
      )}

      {/* プレビュー結果 */}
      {(phase === 'preview' || phase === 'confirmed') && previewResults.length > 0 && (
        <div className="space-y-4 mb-6">
          {/* ステータスバー */}
          <div className="flex items-center gap-2 p-3 rounded-lg bg-white shadow">
            <span className="text-sm font-semibold text-[#374151]">ステータス:</span>
            {phase === 'preview' ? (
              <span className="px-2 py-0.5 bg-orange-100 text-orange-700 rounded text-xs font-bold">プレビュー中（未保存）</span>
            ) : (
              <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded text-xs font-bold">確定済み</span>
            )}
          </div>

          {/* セッション別結果 */}
          {previewResults.map((session) => (
            <div key={session.sessionId} className="bg-white rounded-lg shadow p-4">
              <div className="flex justify-between items-center mb-3">
                <h2 className="font-bold text-lg text-[#374151]">
                  {new Date(session.sessionDate).toLocaleDateString('ja-JP', { month: 'long', day: 'numeric', weekday: 'short' })}
                </h2>
                <div className="flex items-center gap-2">
                  {session.venueName && (
                    <span className="text-xs text-[#6b7280]">{session.venueName}</span>
                  )}
                  {session.capacity && (
                    <span className="text-sm text-[#6b7280]">定員: {session.capacity}名</span>
                  )}
                </div>
              </div>

              {session.matchResults && Object.entries(session.matchResults)
                .sort(([a], [b]) => parseInt(a) - parseInt(b))
                .map(([matchNum, match]) => (
                  <div key={matchNum} className="mb-4 last:mb-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="font-semibold text-sm text-[#374151]">試合{matchNum}</span>
                      {match.lotteryRequired && (
                        <span className="text-xs px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded">抽選あり</span>
                      )}
                    </div>

                    {/* 当選者 */}
                    {match.winners && match.winners.length > 0 && (
                      <div className="mb-2">
                        <div className="text-xs text-[#6b7280] mb-1">当選者 ({match.winners.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.winners.map((p) => (
                            <span key={p.playerId} className="px-2 py-0.5 rounded text-xs bg-green-50 text-green-800 border border-green-200">
                              {p.playerName}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* キャンセル待ち */}
                    {match.waitlisted && match.waitlisted.length > 0 && (
                      <div>
                        <div className="text-xs text-[#6b7280] mb-1">キャンセル待ち ({match.waitlisted.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.waitlisted.map((p) => (
                            <span key={p.playerId} className="px-2 py-0.5 rounded text-xs bg-yellow-50 text-yellow-800 border border-yellow-200">
                              {p.waitlistNumber}. {p.playerName}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
            </div>
          ))}

          {/* 確定ボタン（プレビュー時） */}
          {phase === 'preview' && (
            <div className="flex justify-center gap-3">
              <button
                onClick={() => { setPhase('idle'); setPreviewResults([]); }}
                className="px-4 py-2.5 text-sm border border-[#6b7280] text-[#6b7280] rounded-lg hover:bg-gray-50 transition-colors"
              >
                やり直す
              </button>
              <button
                onClick={handleConfirm}
                disabled={processing === 'confirm'}
                className="flex items-center gap-2 px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold transition-colors disabled:opacity-50"
              >
                <Check size={18} />
                {processing === 'confirm' ? '確定中...' : '結果を確定する'}
              </button>
            </div>
          )}

          {/* 通知送信ボタン（確定後） */}
          {phase === 'confirmed' && (
            <div className="space-y-3">
              <div className="p-3 bg-green-50 text-green-800 border border-green-200 rounded-lg text-sm text-center">
                抽選結果を確定しました。伝助への書き戻しが実行されました。
              </div>
              <div className="flex justify-center gap-3">
                <button
                  onClick={handleNotifyAll}
                  disabled={!!processing}
                  className="flex items-center gap-2 px-4 py-2.5 bg-[#4a6b5a] hover:bg-[#3d5a4c] text-white rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
                >
                  <Bell size={16} />
                  {processing === 'notifyAll' ? '送信中...' : '全員に通知送信'}
                </button>
                <button
                  onClick={handleNotifyWaitlisted}
                  disabled={!!processing}
                  className="flex items-center gap-2 px-4 py-2.5 bg-yellow-600 hover:bg-yellow-700 text-white rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
                >
                  <BellRing size={16} />
                  {processing === 'notifyWaitlisted' ? '送信中...' : 'キャンセル待ちのみ通知'}
                </button>
              </div>

              {/* 通知送信結果 */}
              {notifyResult && (
                <div className="p-3 bg-white rounded-lg shadow text-sm">
                  <div className="font-semibold text-[#374151] mb-1">
                    通知送信結果（{notifyResult.type === 'all' ? '全員' : 'キャンセル待ちのみ'}）
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[#6b7280]">
                    <span>アプリ内通知:</span>
                    <span>{notifyResult.inAppCount}件</span>
                    <span>LINE送信成功:</span>
                    <span>{notifyResult.lineSent}名</span>
                    <span>LINE送信失敗:</span>
                    <span>{notifyResult.lineFailed}名</span>
                    <span>LINEスキップ:</span>
                    <span>{notifyResult.lineSkipped}名</span>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
