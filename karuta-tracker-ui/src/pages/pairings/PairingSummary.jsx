import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { Copy, Check, RefreshCw, Home } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';

/**
 * 札番号は01〜99, 00(=100番)の100枚
 * 2桁ゼロパディングで管理
 */
const ALL_CARDS = Array.from({ length: 100 }, (_, i) => {
  const num = (i + 1) % 100; // 1->01, 2->02, ..., 99->99, 100->00
  return String(num).padStart(2, '0');
});

/** 札番号の1の位を取得 */
const onesDigit = (card) => parseInt(card[1], 10);

/** 札番号の10の位を取得 */
const tensDigit = (card) => parseInt(card[0], 10);

/**
 * 札ルール生成（3試合サイクル）
 * @param {number} totalMatches 試合数
 * @param {Array} [prefix=[]] 既存の札ルール配列。指定時はその末尾から続きを生成して `prefix.concat(extra)` を返す
 * @returns {Array<{type: string, digits: number[], removedCard: string|null, description: string}>}
 */
function generateCardRules(totalMatches, prefix = []) {
  const rules = prefix.length > 0 ? [...prefix] : [];
  let prevUnusedDigits = null; // 前の試合で使わなかった数字
  let prevUsedDigits = null;   // 前の試合で使った数字

  // prefix が与えられた場合、末尾ルールから状態を復元
  if (prefix.length > 0) {
    const last = prefix[prefix.length - 1];
    prevUsedDigits = [...last.digits];
    prevUnusedDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !last.digits.includes(d));
  }

  for (let i = rules.length; i < totalMatches; i++) {
    const cyclePos = i % 3;

    if (cyclePos === 0) {
      // ルール①: 1の位 — 0〜9から5つランダム
      const allDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
      const chosen = pickRandom(allDigits, 5).sort((a, b) => a - b);
      const unused = allDigits.filter(d => !chosen.includes(d));
      prevUnusedDigits = unused;
      prevUsedDigits = chosen;

      rules.push({
        type: 'ones',
        digits: chosen,
        removedCard: null,
        description: `一の位${chosen.join('.')}`,
      });
    } else if (cyclePos === 1) {
      // ルール②: 抜き — 前の試合で使わなかった5つから3つ選ぶ
      const source = prevUnusedDigits || [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
      const chosen = pickRandom(source, 3).sort((a, b) => a - b);

      // 1の位 OR 10の位にchosenが含まれる札 → 51枚
      const matchingCards = ALL_CARDS.filter(card =>
        chosen.includes(onesDigit(card)) || chosen.includes(tensDigit(card))
      );

      // 51枚からランダムに1枚抜く
      const removedCard = matchingCards[Math.floor(Math.random() * matchingCards.length)];

      prevUsedDigits = chosen;
      prevUnusedDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !chosen.includes(d));

      rules.push({
        type: 'nuki',
        digits: chosen,
        removedCard,
        description: `${chosen.join('.')}　${parseInt(removedCard, 10) || 100}抜き`,
      });
    } else {
      // ルール③: 10の位 — 前の試合の3つ以外の7つから5つ選ぶ
      const source = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !prevUsedDigits.includes(d));
      const chosen = pickRandom(source, 5).sort((a, b) => a - b);
      const unused = source.filter(d => !chosen.includes(d));
      prevUnusedDigits = unused;
      prevUsedDigits = chosen;

      rules.push({
        type: 'tens',
        digits: chosen,
        removedCard: null,
        description: `十の位${chosen.join('.')}`,
      });
    }
  }

  return rules;
}

/** 配列からn個ランダムに選ぶ */
function pickRandom(arr, n) {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

/** 名前を全角6文字幅に左詰めパディング（全角スペースで埋める） */
function padName(name, width = 6) {
  if (name.length >= width) return name;
  return name + '\u3000'.repeat(width - name.length);
}

const STORAGE_PREFIX = 'karuta-tracker:card-rules:';

/** localStorage から日付指定の札ルールを復元。失敗時は null を返す */
function loadCardRules(date) {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + date);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** localStorage に日付指定の札ルールを保存。失敗時は黙ってスキップ */
function saveCardRules(date, rules) {
  try {
    localStorage.setItem(STORAGE_PREFIX + date, JSON.stringify(rules));
  } catch {
    // localStorage 不可環境はスキップ
  }
}

/** クライアント端末ローカルタイムの今日（YYYY-MM-DD） */
function getTodayLocalDateStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** STORAGE_PREFIX で始まるキーのうち、今日以外の日付のものを localStorage から削除 */
function cleanupOldCardRules() {
  try {
    const today = getTodayLocalDateStr();
    const toRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(STORAGE_PREFIX)) {
        const keyDate = key.substring(STORAGE_PREFIX.length);
        if (keyDate !== today) toRemove.push(key);
      }
    }
    for (const k of toRemove) localStorage.removeItem(k);
  } catch {
    // 失敗時はスキップ
  }
}

/**
 * 保存済み札ルールと試合数を突き合わせ、表示用配列と保存上書きの要否を返す
 * - 一致: そのまま
 * - 保存が長い: 先頭totalMatches分のみ返す（localStorage 側は保持）
 * - 保存が短い: 末尾に不足分を追加生成（localStorage 側は上書き必要）
 */
function reconcileCardRules(stored, totalMatches) {
  if (stored.length === totalMatches) return { rules: stored, changed: false };
  if (stored.length > totalMatches) {
    return { rules: stored.slice(0, totalMatches), changed: false };
  }
  const extended = generateCardRules(totalMatches, stored);
  return { rules: extended, changed: true };
}

/**
 * テキスト生成
 */
function generateText(date, matchData, cardRules) {
  const d = new Date(date + 'T00:00:00');
  const month = d.getMonth() + 1;
  const day = d.getDate();
  let text = `${month}月${day}日\n`;

  for (let i = 0; i < matchData.length; i++) {
    const match = matchData[i];
    const rule = cardRules[i];
    if (i > 0) text += '\n';
    text += `${i + 1}試合目　${rule ? rule.description : ''}\n`;

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
        const data = await Promise.all(promises);
        setMatchData(data);

        // 札ルール: localStorage 復元優先
        let rules;
        const stored = loadCardRules(date);
        if (stored) {
          const reconciled = reconcileCardRules(stored, totalMatches);
          rules = reconciled.rules;
          if (reconciled.changed) saveCardRules(date, rules);
        } else {
          rules = generateCardRules(totalMatches);
          saveCardRules(date, rules);
        }
        setCardRules(rules);

        // テキスト生成
        const generatedText = generateText(date, data, rules);
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
    saveCardRules(date, rules);
    setCardRules(rules);
    setText(generateText(date, matchData, rules));
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
