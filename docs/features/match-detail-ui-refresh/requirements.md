---
status: completed
---
# 試合詳細画面 UI リファイン 要件定義書

## 1. 概要
- **目的:** 試合詳細画面の情報を整理してコンパクトに表示し、また「メンターからのフィードバックがある場合のみコメント欄を表示する」ことで、メンティー本人にとって余計な空欄を出さないようにする。
- **背景:**
  - 現状、試合結果カード（勝敗・対戦相手・枚数差）と詳細情報カード（試合日・試合番号・会場 など）が縦に分かれて大きな余白を取っており、画面の縦幅を無駄に消費している。
  - メンティー本人画面でも常にコメント欄が表示されるため、メンターからのフィードバックがない試合でも空のコメントスレッドが出てしまい、ノイズになっている。

## 2. ユーザーストーリー
- **対象ユーザー:**
  - **メンティー本人:** 自分の試合詳細を見るとき、コンパクトに情報を確認したい。メンターからフィードバックがあったときだけコメント欄が見えればよい。
  - **メンター（先輩）:** メンティーの試合詳細を見てコメントを残すとき、これまで通りコメント欄から投稿できる必要がある。
- **メンティーのゴール:** 自分の試合結果と情報を一目で把握でき、メンターから何かフィードバックが来たときに気づける。
- **メンターのゴール:** 引き続きメンティーの試合一覧から詳細を開き、コメントを投稿できる。

## 3. 機能要件

### 3.1 試合結果カード（統合カード）

「試合結果」「詳細情報」「お手付き・メモ」を **1つのカード** に統合する。

#### レイアウト
```
┌────────────────────────────────────┐
│                                    │
│   山田太郎  ○ 18           （大きめ） │
│                                    │
│   2026/5/20  第3試合  柏葉体育館    │（小さめテキスト1行）
│ ─────────────────────────────────── │
│   お手付き: 2 回                    │
│   メモ: 札捌もうちょっと            │
│       「そん」の札を取りたい        │
└────────────────────────────────────┘
```

#### 上段（試合結果サマリ）
- 形式: **`{対戦相手名}  {勝敗マーク} {枚数差}`** を1行で表示
- 勝敗マーク: `○`（勝ち） / `×`（負け） / `△`（引き分け）
- 枚数差: マイナス記号や `+` 記号を付けず、**絶対値の整数のみ** を表示
  - 例（差18枚で勝ち）: `山田太郎  ○ 18`
  - 例（差12枚で負け）: `山田太郎  × 12`
  - 例（引き分け）: `山田太郎  △ 0`
- フォントサイズ: 大きめ（現状の `text-4xl` よりは控えめだが、目立つサイズ。`text-2xl` 程度）
- 対戦相手名は **既存どおりタップで「対戦相手の試合一覧」へ遷移可能** とする（現状の挙動を踏襲）
  - ただし `opponentId` が `0` や `null` の場合は通常テキスト表示（現状ロジックを踏襲）
- 勝敗マークは色付きで視認性を保つ（勝:緑、負:赤、分:グレー など、現状の `getResultColor` を踏襲）
- ラベル「対戦相手」「枚数差」は **削除する**

#### 中段（詳細情報1行）
- 形式: `{試合日} {第N試合} {会場名}` を **半角スペース複数（2スペース）** 区切りで1行表示
  - 例: `2026/5/20  第3試合  柏葉体育館`
- 試合日は `YYYY/M/D` 形式（既存の `toLocaleDateString('ja-JP')` で生成される形式を踏襲）
- 試合番号は `第{matchNumber}試合` 形式（現状踏襲）
- 会場名が `null` の場合は `—`（現状踏襲）
- アイコン（Calendar / Trophy / MapPin）は **削除**
- フォントサイズは中程度（`text-sm` 程度）、文字色はグレー系
- 上段との間に縦方向の余白を取る

#### 下段（お手付き・メモ）
- 区切り罫線（`border-t`）を引いた上で表示
- 現状の `myOtetsukiCount` / `myPersonalNotes` 表示を踏襲（ラベル + 値）
- **メンター閲覧時（`isOtherPlayer=true`）の挙動:**
  - 現状は別カード「メンティーのメモ」で `menteeOtetsukiCount` / `menteePersonalNotes` を表示している
  - **この別カードは廃止し、同じ統合カードの下段に同じ書式で表示する**
  - ラベルは現状の「お手付き回数」「メモ」を踏襲（カードタイトルなしで `お手付き` `メモ` のみ）
- 「お手付き回数」「メモ」がともに null の場合は下段（罫線含む）を出さない

#### 削除する要素
- カードフッターの「作成日 / 更新日」表示は **削除**

### 3.2 コメント欄（`MatchCommentThread`）の表示制御

| 閲覧パターン | 現状 | 変更後 |
|--------------|------|--------|
| メンター閲覧時（`isOtherPlayer=true`） | メンター関係（ACTIVE）があれば表示 | **変更なし**（現状どおり表示） |
| メンティー本人画面（`isOtherPlayer=false`） | 常に表示 | **自分以外の投稿者によるコメントが1件以上ある場合のみ表示**。0件なら投稿フォーム含め完全非表示 |

#### 「自分以外のコメント」の判定範囲
- `MatchCommentDto.authorId !== currentPlayer.id` であるコメントが **1件以上存在するか** をチェック
- メンター関係の解除済み・現役問わず、過去にもらったコメントが残っていれば「あり」とみなす
- → 既存の `GET /api/matches/{matchId}/comments?menteeId={menteeId}` のレスポンスに含まれるコメントの `authorId` で判定する

### 3.3 ヘッダー・編集/削除ボタンなど他の要素
- ページヘッダー（`PageHeader`）、編集/削除ボタンの非表示制御、削除確認モーダルは **現状どおり変更なし**
- ヘッダー直下の「試合日（YYYY年M月D日）」表示も **現状どおり残す**（カード内の `2026/5/20` 表示と重複するが、ヘッダー要素として価値があるため残す。※後述「設計判断の根拠」参照）

## 4. 技術設計

### 4.1 API設計
- **変更なし**
- 既存 `GET /api/matches/{matchId}/comments?menteeId={menteeId}` を、メンティー本人画面でも呼び出してコメント有無を判定する

### 4.2 DB設計
- **変更なし**

### 4.3 フロントエンド設計

#### 変更ファイル
- `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx`

#### 変更内容

**(A) 試合結果・詳細情報・メモを1つのカードに統合**

現状の以下の3つを1つの `<div className="bg-white rounded-lg shadow-sm p-6 ...">` カードに統合する:
- 試合結果カード（[MatchDetail.jsx:157-204](karuta-tracker-ui/src/pages/matches/MatchDetail.jsx#L157-L204)）
- 詳細情報カード（[MatchDetail.jsx:206-273](karuta-tracker-ui/src/pages/matches/MatchDetail.jsx#L206-L273)）
- メンティーのメモカード（メンター閲覧時のみ表示。[MatchDetail.jsx:276-293](karuta-tracker-ui/src/pages/matches/MatchDetail.jsx#L276-L293)）

統合カードの構造:
```jsx
<div className="bg-white rounded-lg shadow-sm p-6 mb-6 space-y-4">
  {/* 上段: 試合結果サマリ */}
  <div className="text-center">
    {/* 対戦相手 名前ボタン or テキスト */}
    <span className="text-2xl font-semibold">{opponentName}</span>
    {/* 勝敗マーク + 枚数差 (色付き) */}
    <span className={`text-2xl font-bold ml-3 ${getResultColor(...)}`}>
      {markFromResult(match.result)} {Math.abs(match.scoreDifference)}
    </span>
  </div>

  {/* 中段: 詳細情報1行 */}
  <div className="text-sm text-gray-600 text-center">
    {matchDate}  第{matchNumber}試合  {venueName || '—'}
  </div>

  {/* 下段: お手付き・メモ */}
  {(otetsukiCount != null || personalNotes) && (
    <div className="pt-4 border-t border-gray-200 space-y-3">
      {otetsukiCount != null && <div>お手付き: {otetsukiCount} 回</div>}
      {personalNotes && <div>メモ: <pre>{personalNotes}</pre></div>}
    </div>
  )}
</div>
```

- 表示するお手付き回数・メモは `isOtherPlayer` の値に応じて切り替える:
  - メンター閲覧時（`isOtherPlayer=true`）: `menteeOtetsukiCount` / `menteePersonalNotes` を使用
  - メンティー本人（`isOtherPlayer=false`）: `myOtetsukiCount` / `myPersonalNotes` を使用

**(B) 勝敗マーク変換ヘルパー**

```js
const getResultMark = (result) => {
  switch (result) {
    case '勝ち': return '○';
    case '負け': return '×';
    case '引き分け': return '△';
    default: return result;
  }
};
```

**(C) コメント欄表示判定の追加**

- `MatchDetail` で `matchCommentsAPI.getComments(matchId, menteeIdForComments)` を呼んでコメント一覧を取得する
- メンティー本人画面（`isOtherPlayer=false`）の場合、取得したコメントに `authorId !== currentPlayer.id` のものが1件以上あれば `MatchCommentThread` を表示、なければ非表示
- メンター閲覧時（`isOtherPlayer=true`）は現状ロジックを維持（`hasMentorRelation && menteeIdForComments` で表示）

```js
const [commentsByOthersExist, setCommentsByOthersExist] = useState(false);

useEffect(() => {
  if (!menteeIdForComments) return;
  matchCommentsAPI.getComments(Number(id), menteeIdForComments)
    .then(res => {
      const hasOthers = res.data.some(c => c.authorId !== currentPlayer?.id);
      setCommentsByOthersExist(hasOthers);
    })
    .catch(() => setCommentsByOthersExist(false));
}, [id, menteeIdForComments, currentPlayer?.id]);

// 表示判定
const showCommentThread = isOtherPlayer
  ? (hasMentorRelation && menteeIdForComments)
  : (menteeIdForComments && commentsByOthersExist);
```

- 既存の `MatchCommentThread` 内部の `fetchComments` はそのまま残す（独自に再フェッチして投稿・更新後の再表示を行うため）。`MatchDetail` 側のフェッチはあくまで「表示する/しない」の判定にのみ使う。
- N+1 的なリクエスト重複は許容する（試合詳細1回開いて2回叩く程度、コメントAPIの負荷は低い）

**(D) 削除する要素**
- カードフッターの「作成日 / 更新日」表示
- 別カードの「メンティーのメモ」セクション（統合カードに統合済み）

#### 影響しないコンポーネント
- `MatchCommentThread.jsx` 自体には変更を加えない
- `MatchList.jsx`、その他は変更なし

### 4.4 バックエンド設計
- 変更なし

## 5. 影響範囲

### 変更が必要な既存ファイル
| ファイル | 変更内容 |
|---------|---------|
| `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` | カード統合、勝敗表示変更、詳細情報1行化、コメント表示判定追加、作成日/更新日削除、メンティーのメモ別カード廃止 |

### 既存機能への影響
- **試合詳細画面の見た目が大きく変わる**（情報は欠落しないが、レイアウトと表示密度が変わる）
- メンティー本人画面でコメント欄が表示されなくなるケースが発生（メンターからコメントが来ていない試合では消える）
  - これは要件通りの挙動だが、既存ユーザーには「コメント欄が消えた」と見える可能性あり
- 試合結果カード内の `Trophy` `User` `Calendar` `MapPin` アイコンが消える
  - `lucide-react` の import から不要分は削除する

### バックエンド・DBへの影響
- なし

### テストへの影響
- `karuta-tracker-ui/src/pages/matches/MatchCommentThread.test.jsx` には影響なし（コンポーネント自体は無変更）
- `MatchDetail.jsx` のテストは現状存在しないため、新規追加は不要（既存方針踏襲）

## 6. 設計判断の根拠

### なぜ「ヘッダー直下の試合日」を残すのか
- カード内に `2026/5/20` 表示があるため重複しているように見えるが、ヘッダー直下の表示は **試合のメタ情報としてページの主題を示す役割** を果たしている。
- 削除すると「いつの試合か」の第一印象が失われるため、現状維持とする。

### なぜ「自分以外の人のコメント」で判定するのか（解除済みメンター含む）
- ユーザーから「読を問わず全てのコメント」を判定対象にする指示があったため。
- 過去にメンター関係を結んでいた人からもらったコメントは記録として残しているケースを想定。
- 解除済みメンターであっても、過去にコメントをもらった事実があれば、メンティーにとって「フィードバックを受けた試合」として扱われるべき。

### なぜ MatchDetail 側で `matchCommentsAPI` を再フェッチするのか（MatchCommentThread に統合しないのか）
- `MatchCommentThread` を表示するかどうかを **親で判断する** ためには、親側でコメント有無を取得する必要がある。
- もし `MatchCommentThread` 内部で「自分以外のコメントが0件ならコンポーネント全体を null にする」設計にすると、コンポーネントが「描画されないコンポーネント」の責務を持ち、親が制御不能になる。
- API呼び出しは2回になるが、コメント取得APIは軽量で、画面表示時の数回の追加リクエストは許容可能。

### なぜカードを1つに統合するのか
- ユーザーから「1つのカードにまとめる」指示があったため。
- 試合結果と詳細情報は同じ試合に関する情報であり、論理的に分割する必要が薄い。
- カードを分けると `mb-6` の余白で縦幅が増えて視認性が落ちる。
- メンティーのメモ（メンター閲覧時）も「その試合のメモ」として同じカードに収めるのが自然。

### なぜ枚数差にマイナス記号を付けないのか
- ユーザー指示。`× 18` の方が `× -18` よりも見た目がシンプル。
- 勝敗マーク（○/×）で勝ち負けは明示されており、枚数差が負の数になることはあり得ない（枚数差は絶対値で十分意味が通じる）。
