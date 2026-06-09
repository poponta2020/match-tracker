---
status: completed
---
# 札ルールの日付別永続化（pairing-card-rule-persistence）要件定義書

## 1. 概要

- **目的:** 同一日内であれば、対戦組み合わせを何度作り直しても「札ルール一覧」画面に表示される札ルール（一の位/十の位/抜き）が変わらないようにする
- **背景:** 12時に対戦と札ルールを LINE で配信した後、メンバー変更があると対戦を再生成して再配信する運用がある。現状は「札ルール一覧」画面を開くたびに札ルールがランダム再生成されるため、再配信時に札ルールが組み変わってしまい、初回配信時に伝えた札ルールと食い違って参加者に混乱を与える

## 2. ユーザーストーリー

- **対象ユーザー:** 練習会の管理者（同一人物が同一端末・同一ブラウザで運用）
- **ユーザーの目的:** 12時に流した対戦+札ルールの後、メンバー変更後の対戦のみを再配信したい
- **利用シナリオ:**
  1. 管理者が朝、対戦組み合わせを生成し「札ルール一覧」画面を開く
  2. 札ルールを含むテキストをコピーして LINE に流す（12時時点の配信）
  3. その後メンバー変更で対戦を再生成する
  4. 管理者が再度「札ルール一覧」画面を開いてテキストをコピーし、LINE に再配信する
  5. このとき**札ルールは1の手順と同一**で、対戦のみが最新のものになっていてほしい

## 3. 機能要件

### 3.1 画面仕様

#### 札ルール一覧画面（既存画面の挙動変更）

**画面ロード時の挙動:**
- localStorage に「その日（URLの `date` パラメータの日付）」の札ルールが保存されていれば、それを復元して表示する
- 保存されていなければ、現状どおり新規生成して localStorage に保存する
- 画面ロード時、localStorage 内の「**システム日付の今日**と一致しない日付の札ルールデータ」はすべて削除する

**試合数と保存済み札ルール長が不一致の場合のフォールバック:**
- 保存済みが短い（例: 保存3試合分、現セッション4試合）: 既存3試合分は保存値を使用し、不足分（4試合目）のみ新規生成して末尾追加・localStorage に上書き保存
- 保存済みが長い（例: 保存5試合分、現セッション3試合）: 先頭から現セッション分（3試合分）のみ表示し、超過分（4・5試合目）は localStorage 上は残しても表示しない（次回試合数が戻れば再利用可能）

**「札を再生成」ボタンの挙動:**
- 押下時に `window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')` で確認する
- OK の場合のみ、現状どおり再生成し、localStorage に上書き保存する
- キャンセルの場合は何もしない

### 3.2 ビジネスルール

- 札ルールの保存単位は「日付（YYYY-MM-DD）」とする
- 同日複数練習セッションの可能性は考慮しない（URLの `date` パラメータが同じであれば同じ札ルールを返す）
- 「システム日付の今日」の判定はクライアント端末のローカルタイム（実質 JST）で行う

### 3.3 エラーケース・例外処理

- localStorage のパース失敗（JSON 不正・破損データ）: try-catch で握って、その日のデータは破棄して新規生成する
- localStorage が利用できない環境（極端なプライバシー設定等）: try-catch で握って、保存をスキップする（毎回ランダム生成にフォールバック）
- 保存値の構造が想定と違う（バージョン違い等）: 破棄して新規生成する

## 4. 技術設計

### 4.1 API設計

- なし（バックエンド変更なし）

### 4.2 DB設計

- なし（DB変更なし）

### 4.3 フロントエンド設計

#### 修正対象ファイル

- `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx`

#### localStorage キー設計

- キー名: `karuta-tracker:card-rules:<YYYY-MM-DD>`
  - 例: `karuta-tracker:card-rules:2026-06-09`
  - `karuta-tracker:` プレフィックスは既存の localStorage 利用（認証関連）との衝突防止用
- 保存値: `JSON.stringify(cardRules)` — 既存 `generateCardRules()` の戻り値配列をそのままシリアライズ

#### 主要関数の追加・変更

**新規ユーティリティ関数（同ファイル内）:**

```js
const STORAGE_PREFIX = 'karuta-tracker:card-rules:';

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

function saveCardRules(date, rules) {
  try {
    localStorage.setItem(STORAGE_PREFIX + date, JSON.stringify(rules));
  } catch {
    // localStorage 不可環境はスキップ
  }
}

/** システム日付の今日（YYYY-MM-DD, ローカルタイム） */
function getTodayLocalDateStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** 古いキーを削除：システム日付の今日と一致しない `karuta-tracker:card-rules:` キーを全削除 */
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

/** 試合数と保存済み札ルールを突き合わせ、不一致なら埋め合わせ */
function reconcileCardRules(stored, totalMatches) {
  if (stored.length === totalMatches) return { rules: stored, changed: false };
  if (stored.length > totalMatches) {
    // 表示は先頭totalMatches分のみ、localStorage 側は保持する
    return { rules: stored.slice(0, totalMatches), changed: false };
  }
  // 短い場合: 不足分のみ末尾に追加生成し、localStorage を上書き
  const extended = generateCardRules(totalMatches, stored);
  return { rules: extended, changed: true };
}
```

**`generateCardRules` の引数拡張:**

3試合サイクルの「前試合で使った数字」を参照するロジックのため、不足分のみ生成するときは「保存済み末尾の状態」から続きを生成する必要がある。
既存の `generateCardRules(totalMatches)` を `generateCardRules(totalMatches, prefix = [])` に拡張し、`prefix` がある場合はその末尾から状態を引き継いで残り（`totalMatches - prefix.length` 試合分）を追加する。

**画面ロード時の `useEffect` の変更:**

```js
useEffect(() => {
  if (!date) return;

  const fetchData = async () => {
    setLoading(true);
    try {
      // 古いキーのクリーンアップ
      cleanupOldCardRules();

      const sessionRes = await practiceAPI.getByDate(date);
      const totalMatches = sessionRes.data?.totalMatches || 3;

      // 対戦データ取得（既存処理）
      const promises = Array.from({ length: totalMatches }, (_, i) =>
        pairingAPI.getByDateAndMatchNumber(date, i + 1)
          .then(res => ({ matchNumber: i + 1, pairings: res.data || [] }))
          .catch(() => ({ matchNumber: i + 1, pairings: [] }))
      );
      const data = await Promise.all(promises);
      setMatchData(data);

      // 札ルール: localStorage 優先で取得
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

      // テキスト生成（既存処理）
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
```

**`handleRegenerate` の変更:**

```js
const handleRegenerate = () => {
  if (!window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')) return;
  const rules = generateCardRules(matchData.length);
  saveCardRules(date, rules);
  setCardRules(rules);
  setText(generateText(date, matchData, rules));
};
```

### 4.4 バックエンド設計

- 変更なし

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

- `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx`

### 5.2 既存機能への影響

- **対戦組み合わせ機能（PairingGenerator）:** 影響なし。札ルールは PairingSummary 画面でのみ扱う
- **LINE通知設定（自動通知）:** 影響なし。札ルールは現状アプリ自動配信ではなく手動コピー運用
- **テスト:** PairingSummary 専用の単体テストは現状なし（要 Grep 確認）。導入時に localStorage 周りの単体テストを追加することを推奨

### 5.3 互換性

- 既存ユーザーの localStorage には何も入っていない状態 → 初回のみ生成して保存する流れになるため、初回利用時の挙動は現状と完全に同一（破壊的変更なし）
- 2回目以降の同日アクセスで札ルールが固定される、というのが本機能の効果

## 6. 設計判断の根拠

- **localStorage 採用の理由:** ユーザー運用が「同一管理者・同一端末・PWAホーム画面追加」であり、別端末・別ブラウザでの同期は不要。DB に保存するコスト（テーブル設計・マイグレーション・本番DB適用・APIエンドポイント追加）に見合わない。CLAUDE.md の「DB保存推奨」というベストプラクティスから外れる懸念は事前にユーザーへ提示済み（端末/ブラウザ変更時の制約・キャッシュクリア時の消失・PWA分離の可能性等）、ユーザー承諾済み
- **キー命名 `karuta-tracker:card-rules:<YYYY-MM-DD>`:** 既存 localStorage（`auth.js` 等）との衝突を避けるためアプリ名プレフィックスを採用。日付ごと管理はユーザー回答で確定
- **古いキーの削除タイミング:** 画面ロード時にまとめて削除。専用のスケジューラやサービスワーカーは導入しない（シンプルさ優先）。判定軸は「システム日付の今日と一致しないキー」を削除、つまり過去日にアクセスしてもそのデータは保存しない動き
- **試合数不一致時のフォールバック方針（保存済み優先・足りなければ追加・余れば切り捨て）:** ユーザー回答で確定。`generateCardRules` が3試合サイクル（前試合の数字を参照）であるため、続きから生成するロジックを追加実装する必要がある
- **「札を再生成」の確認ダイアログ:** 既存コードベースで `window.confirm()` が一般的に使われているためそれを踏襲。専用モーダルコンポーネントはコードベース上存在しない
- **保存対象は札ルールのみ（テキスト全体は保存しない）:** ユーザー回答で確定。テキストは画面ロード時に最新の対戦データ＋札ルールから再生成することで、対戦変更が自動反映される
