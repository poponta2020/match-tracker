import { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { lotteryAPI } from '../../api/lottery';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * 繰り上げ参加承認画面（統合オファー対応）
 */
export default function OfferResponse() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const participantId = searchParams.get('id');
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);
  const [offerDetail, setOfferDetail] = useState(null);
  const [allOffers, setAllOffers] = useState([]);
  const [responded, setResponded] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  // オファー詳細を取得し、同一セッションの他のOFFEREDも取得
  const fetchOffers = useCallback(async () => {
    if (!participantId) {
      setFetching(false);
      return;
    }
    try {
      const res = await lotteryAPI.getOfferDetail(Number(participantId));
      setOfferDetail(res.data);

      // 同一セッションの全OFFEREDを取得
      if (res.data.sessionId) {
        try {
          const offersRes = await lotteryAPI.getSessionOffers(res.data.sessionId);
          setAllOffers(offersRes.data || []);
        } catch {
          // フォールバック: 元のオファーのみ表示
          setAllOffers(res.data.status === 'OFFERED' ? [res.data] : []);
        }
      }
    } catch (err) {
      console.error('Failed to fetch offer detail:', err);
      setError(err.response?.data?.message || 'オファー情報の取得に失敗しました');
    } finally {
      setFetching(false);
    }
  }, [participantId]);

  useEffect(() => {
    fetchOffers();
  }, [fetchOffers]);

  // 最も遅い応答期限
  const latestDeadline = allOffers.length > 0
    ? allOffers.reduce((latest, o) => {
        if (!o.offerDeadline) return latest;
        const d = new Date(o.offerDeadline);
        return !latest || d > latest ? d : latest;
      }, null)
    : offerDetail?.offerDeadline ? new Date(offerDetail.offerDeadline) : null;

  const isExpired = latestDeadline ? latestDeadline < new Date() : false;
  const isProcessed = offerDetail && offerDetail.status !== 'OFFERED' && allOffers.length === 0;

  // 個別参加
  const handleRespondSingle = async (pid) => {
    setLoading(true);
    setError(null);
    try {
      await lotteryAPI.respondOffer(pid, true);
      // 残りオファーを再取得
      const offersRes = await lotteryAPI.getSessionOffers(offerDetail.sessionId);
      const remaining = offersRes.data || [];
      if (remaining.length === 0) {
        setResponded(true);
        setResult('accepted');
      } else {
        setAllOffers(remaining);
      }
    } catch (err) {
      console.error('Failed to respond to offer:', err);
      setError(err.response?.data?.message || '応答に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // 一括参加
  const handleAcceptAll = async () => {
    if (!offerDetail?.sessionId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await lotteryAPI.respondOfferAll(offerDetail.sessionId, true);
      const acceptedCount = res.data?.count ?? allOffers.length;
      if (acceptedCount < allOffers.length) {
        // 部分成功（期限切れ分がスキップされた）→ 残りを再取得
        const offersRes = await lotteryAPI.getSessionOffers(offerDetail.sessionId);
        const remaining = offersRes.data || [];
        if (remaining.length > 0) {
          setAllOffers(remaining);
          setError(`${acceptedCount}件を承諾しましたが、期限切れのオファーがあります`);
        } else {
          setResponded(true);
          setResult('accepted');
        }
      } else {
        setResponded(true);
        setResult('accepted');
      }
    } catch (err) {
      console.error('Failed to accept all offers:', err);
      setError(err.response?.data?.message || '応答に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // 一括辞退
  const handleDeclineAll = async () => {
    if (!offerDetail?.sessionId) return;
    setLoading(true);
    setError(null);
    try {
      await lotteryAPI.respondOfferAll(offerDetail.sessionId, false);
      setResponded(true);
      setResult('declined');
    } catch (err) {
      console.error('Failed to decline all offers:', err);
      setError(err.response?.data?.message || '応答に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  if (!participantId) {
    return (
      <div className="max-w-md mx-auto p-4 text-center">
        <p className="text-gray-500">無効なURLです</p>
      </div>
    );
  }

  if (fetching) return <LoadingScreen />;

  if (!offerDetail && !fetching) {
    return (
      <div className="max-w-md mx-auto p-4 text-center">
        <div className="p-6 rounded-lg bg-red-50">
          <h2 className="text-xl font-bold mb-2 text-red-700">オファー情報を取得できませんでした</h2>
          <p className="text-gray-600 mb-4">{error || 'オファーが見つかりません'}</p>
          <button
            onClick={() => navigate('/')}
            className="px-4 py-2 bg-[#4a6b5a] text-white rounded hover:opacity-90">
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  if (responded) {
    return (
      <div className="max-w-md mx-auto p-4 text-center">
        <div className={`p-6 rounded-lg ${result === 'accepted' ? 'bg-green-50' : 'bg-gray-50'}`}>
          <div className="text-4xl mb-4">{result === 'accepted' ? '✓' : '—'}</div>
          <h2 className="text-xl font-bold mb-2">
            {result === 'accepted' ? '参加が確定しました' : '辞退しました'}
          </h2>
          <p className="text-gray-600 mb-4">
            {result === 'accepted'
              ? '練習への参加が確定しました。'
              : '次のキャンセル待ちの方に通知されます。'}
          </p>
          <button
            onClick={() => navigate('/')}
            className="px-4 py-2 bg-[#4a6b5a] text-white rounded hover:opacity-90">
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  // 処理済み表示（元のオファーが応答済み かつ 残りOFFEREDなし）
  if (isProcessed) {
    return (
      <div className="max-w-md mx-auto p-4 text-center">
        <div className="p-6 rounded-lg bg-gray-50">
          <div className="text-4xl mb-4">—</div>
          <h2 className="text-xl font-bold mb-2">このオファーは処理済みです</h2>
          <p className="text-gray-600 mb-4">
            現在のステータス: {offerDetail.status === 'WON' ? '当選' : offerDetail.status === 'DECLINED' ? '辞退' : offerDetail.status}
          </p>
          <button
            onClick={() => navigate('/')}
            className="px-4 py-2 bg-[#4a6b5a] text-white rounded hover:opacity-90">
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  // 対象オファーなし（一括辞退後のリンク再訪等）
  if (offerDetail && allOffers.length === 0) {
    return (
      <div className="max-w-md mx-auto p-4 text-center">
        <div className="p-6 rounded-lg bg-gray-50">
          <div className="text-4xl mb-4">—</div>
          <h2 className="text-xl font-bold mb-2">応答可能なオファーはありません</h2>
          <button
            onClick={() => navigate('/')}
            className="px-4 py-2 bg-[#4a6b5a] text-white rounded hover:opacity-90">
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto p-4">
      <div className="bg-white rounded-lg shadow p-6">
        <h1 className="text-xl font-bold mb-4 text-center">繰り上げ参加のご連絡</h1>

        {/* オファー詳細情報 */}
        {offerDetail && (
          <div className="mb-4 p-3 bg-blue-50 rounded text-sm space-y-1">
            <div className="font-bold text-gray-800">
              {new Date(offerDetail.sessionDate).toLocaleDateString('ja-JP', {
                year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'
              })}
            </div>
            {offerDetail.venueName && (
              <div className="text-gray-600">{offerDetail.venueName}</div>
            )}
            <div className="text-gray-600">
              {allOffers.length > 0
                ? `第${allOffers.map(o => o.matchNumber).join('・')}試合`
                : `第${offerDetail.matchNumber}試合`}
              {offerDetail.startTime && ` / ${offerDetail.startTime}〜${offerDetail.endTime}`}
            </div>
            {latestDeadline && (
              <div className={`font-bold ${isExpired ? 'text-red-600' : 'text-blue-700'}`}>
                応答期限: {latestDeadline.toLocaleString('ja-JP')}
              </div>
            )}
          </div>
        )}

        {/* 期限切れ表示 */}
        {isExpired ? (
          <div className="mb-4 p-3 bg-red-50 text-red-700 rounded text-sm font-bold text-center">
            応答期限が過ぎています
          </div>
        ) : (
          <p className="text-gray-700 mb-6 text-center">
            練習に空きが出ました。参加しますか？
          </p>
        )}

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-700 rounded text-sm">{error}</div>
        )}

        <div className="flex flex-col gap-3">
          {/* 個別参加ボタン */}
          {allOffers.map(offer => (
            <button
              key={offer.participantId}
              onClick={() => handleRespondSingle(offer.participantId)}
              disabled={loading || isExpired}
              className="w-full py-3 bg-[#27AE60] text-white rounded-lg font-bold hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed">
              {loading ? '...' : `${offer.matchNumber}試合目に参加`}
            </button>
          ))}

          {/* すべての試合に参加ボタン（2試合以上のみ） */}
          {allOffers.length >= 2 && (
            <button
              onClick={handleAcceptAll}
              disabled={loading || isExpired}
              className="w-full py-3 bg-[#2E86C1] text-white rounded-lg font-bold hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed">
              {loading ? '...' : 'すべての試合に参加'}
            </button>
          )}

          {/* 辞退ボタン */}
          <button
            onClick={handleDeclineAll}
            disabled={loading || isExpired}
            className="w-full py-3 bg-[#E74C3C] text-white rounded-lg font-bold hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed">
            {loading ? '...' : '辞退する'}
          </button>
        </div>
      </div>
    </div>
  );
}
