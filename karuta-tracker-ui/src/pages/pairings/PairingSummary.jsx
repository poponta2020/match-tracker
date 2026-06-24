import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { byeActivityAPI } from '../../api/byeActivities';
import { Copy, Check, RefreshCw, Home } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';
import {
  generateCardRules,
  loadCardRules,
  saveCardRules,
  getTodayLocalDateStr,
  cleanupOldCardRules,
  reconcileCardRules,
} from './cardRules';

// 札ルール関連の純粋関数は ./cardRules.js に extract（テスト可能化のため）。
// PairingSummary 専用の関数（padName / generateText）のみ本ファイルに残す。

/** 名前を全角6文字幅に左詰めパディング（全角スペースで埋める） */
function padName(name, width = 6) {
  if (name.length >= width) return name;
  return name + '　'.repeat(width - name.length);
}

/**
 * 抜け番活動の配列から「試合番号 → 読手名の配列」マップを構築する。
 * 活動種別が READING（読み）のものだけを読手として対象にする。
 */
function buildReadersByMatch(activities) {
  const map = {};
  for (const a of activities) {
    if (a.activityType !== 'READING') continue;
    if (a.matchNumber == null || !a.playerName) continue;
    if (!map[a.matchNumber]) map[a.matchNumber] = [];
    map[a.matchNumber].push(a.playerName);
  }
  return map;
}

/**
 * テキスト生成
 * @param {Object} readersByMatch { [試合番号]: [読手名, ...] }（読みに設定された抜け番選手）
 */
function generateText(date, matchData, cardRules, readersByMatch = {}) {
  const d = new Date(date + 'T00:00:00');
  const month = d.getMonth() + 1;
  const day = d.getDate();
  let text = `${month}月${day}日\n`;

  for (let i = 0; i < matchData.length; i++) {
    const match = matchData[i];
    const rule = cardRules[i];
    if (i > 0) text += '\n';
    text += `${i + 1}試合目　${rule ? rule.description : ''}\n`;

    const readers = readersByMatch[i + 1];
    if (readers && readers.length > 0) {
      text += `【読手：${readers.join('、')}】\n`;
    }

    for (const pairing of match.pairings) {
      text += `${padName(pairing.player1Name)}　${padName(pairing.player2Name)}\n`;
    }
  }

  return text.trimEnd();
}

const PairingSummary = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const date = searchParams.get('date');
  const [loading, setLoading] = useState(true);
  const [matchData, setMatchData] = useState([]);
  const [cardRules, setCardRules] = useState([]);
  const [readersByMatch, setReadersByMatch] = useState({});
  const [text, setText] = useState('');
  const [copied, setCopied] = useState(false);
  const textareaRef = useRef(null);

  useEffect(() => {
    if (!date) return;

    const fetchData = async () => {
      setLoading(true);
      try {
        // 古い日付の札ルールを localStorage から削除
        cleanupOldCardRules();

        const sessionRes = await practiceAPI.getByDate(date);
        const totalMatches = sessionRes.data?.totalMatches || 3;

        // 全試合の組み合わせを取得
        const promises = Array.from({ length: totalMatches }, (_, i) =>
          pairingAPI.getByDateAndMatchNumber(date, i + 1)
            .then(res => ({ matchNumber: i + 1, pairings: res.data || [] }))
            .catch(() => ({ matchNumber: i + 1, pairings: [] }))
        );
        // 抜け番活動（読み）を取得し「試合番号→読手名」マップを構築（失敗時は読手なしで継続）
        const byePromise = byeActivityAPI.getByDate(date)
          .then(res => buildReadersByMatch(res.data || []))
          .catch(() => ({}));
        const [data, readers] = await Promise.all([Promise.all(promises), byePromise]);
        setMatchData(data);
        setReadersByMatch(readers);

        // 札ルール: localStorage 復元優先
        // 過去日（date !== 今日）の場合は保存しない（cleanupOldCardRules の方針と整合）
        const canPersist = date === getTodayLocalDateStr();
        let rules;
        const stored = loadCardRules(date);
        if (stored) {
          const reconciled = reconcileCardRules(stored, totalMatches);
          rules = reconciled.rules;
          if (reconciled.changed && canPersist) saveCardRules(date, rules);
        } else {
          rules = generateCardRules(totalMatches);
          if (canPersist) saveCardRules(date, rules);
        }
        setCardRules(rules);

        // テキスト生成
        const generatedText = generateText(date, data, rules, readers);
        setText(generatedText);
      } catch (err) {
        console.error('Failed to fetch pairing data:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [date]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // フォールバック
      if (textareaRef.current) {
        textareaRef.current.select();
        document.execCommand('copy');
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    }
  };

  const handleRegenerate = () => {
    if (!window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')) return;
    const rules = generateCardRules(matchData.length);
    // 過去日（date !== 今日）の場合は保存しない（cleanupOldCardRules の方針と整合）
    if (date === getTodayLocalDateStr()) saveCardRules(date, rules);
    setCardRules(rules);
    setText(generateText(date, matchData, rules, readersByMatch));
  };

  if (loading) {
    return (
      <>
        <PageHeader title="札ルール一覧" backTo="/pairings" />
        <LoadingScreen />
      </>
    );
  }

  return (
    <>
      <PageHeader title="札ルール一覧" backTo="/pairings" />
      <div className="space-y-4">
      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-[#374151]">LINE送信用テキスト</h2>
          <div className="flex gap-2">
            <button
              onClick={handleRegenerate}
              className="flex items-center gap-1.5 text-sm text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1.5 rounded-lg hover:bg-[#e5ebe7] transition-colors"
            >
              <RefreshCw className="w-4 h-4" />
              札を再生成
            </button>
            <button
              onClick={handleCopy}
              className={`flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-lg transition-colors ${
                copied
                  ? 'bg-[#4a6b5a] text-white'
                  : 'bg-[#4a6b5a] text-white hover:bg-[#3d5a4c]'
              }`}
            >
              {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              {copied ? 'コピーしました' : 'コピー'}
            </button>
          </div>
        </div>

        <textarea
          ref={textareaRef}
          value={text}
          onChange={(e) => setText(e.target.value)}
          className="w-full h-80 px-4 py-3 border border-[#c5cec8] rounded-lg font-mono text-sm text-[#374151] leading-relaxed resize-y focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
        />

        <p className="text-xs text-[#6b7280]">
          テキストは自由に編集できます。「札を再生成」で札ルールのみ再ランダムします。
        </p>
      </div>

      <button
        onClick={() => navigate('/')}
        className="w-full flex items-center justify-center gap-2 bg-[#4a6b5a] text-white py-3 rounded-lg hover:bg-[#3d5a4c] transition-colors font-medium"
      >
        <Home className="w-5 h-5" />
        ホームに戻る
      </button>
      </div>
    </>
  );
};

export default PairingSummary;
