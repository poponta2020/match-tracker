import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { lotteryAPI } from '../../api/lottery';

/**
 * 繰り上げ参加承認画面
 */
export default function OfferResponse() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const participantId = searchParams.get('id');
  const [loading, setLoading] = useState(false);
  const [responded, setResponded] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleRespond = async (accept) => {
    if (!participantId) return;
    setLoading(true);
    setError(null);
    try {
      await lotteryAPI.respondOffer(Number(participantId), accept);
      setResponded(true);
      setResult(accept ? 'accepted' : 'declined');
    } catch (err) {
      console.error('Failed to respond to offer:', err);
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

  return (
    <div className="max-w-md mx-auto p-4">
      <div className="bg-white rounded-lg shadow p-6">
        <h1 className="text-xl font-bold mb-4 text-center">繰り上げ参加のご連絡</h1>

        <p className="text-gray-700 mb-6 text-center">
          練習に空きが出ました。参加しますか？
        </p>

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-700 rounded text-sm">{error}</div>
        )}

        <div className="flex gap-3">
          <button
            onClick={() => handleRespond(true)}
            disabled={loading}
            className="flex-1 py-3 bg-[#4a6b5a] text-white rounded-lg font-bold hover:opacity-90 disabled:opacity-50">
            {loading ? '...' : '参加する'}
          </button>
          <button
            onClick={() => handleRespond(false)}
            disabled={loading}
            className="flex-1 py-3 bg-gray-200 text-gray-700 rounded-lg font-bold hover:bg-gray-300 disabled:opacity-50">
            {loading ? '...' : '参加しない'}
          </button>
        </div>
      </div>
    </div>
  );
}
