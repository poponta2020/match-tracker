import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { Copy, Check, ArrowLeft, RefreshCw, Home } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

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
 * @returns {Array<{type: string, digits: number[], removedCard: string|null, description: string}>}
 */
function generateCardRules(totalMatches) {
  const rules = [];
  let prevUnusedDigits = null; // 前の試合で使わなかった数字
  let prevUsedDigits = null;   // 前の試合で使った数字

  for (let i = 0; i < totalMatches; i++) {
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

        // 札ルール生成
        const rules = generateCardRules(totalMatches);
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
    const rules = generateCardRules(matchData.length);
    setCardRules(rules);
    setText(generateText(date, matchData, rules));
  };

  if (loading) {
    return <LoadingScreen />;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate('/pairings')}
          className="flex items-center gap-1 text-[#4a6b5a] hover:text-[#3d5a4c] text-sm"
        >
          <ArrowLeft className="w-4 h-4" />
          組み合わせに戻る
        </button>
      </div>

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
  );
};

export default PairingSummary;
