---
status: completed
---
# match-list-opponent-link 要件定義書

## 1. 概要

### 目的
対戦一覧画面（`/matches`）の各対戦行から、**対戦相手の対戦履歴**と**その対戦の詳細**へワンタップで遷移できるようにする。

### 背景・動機
- 現状、対戦一覧の行をタップすると一律に対戦詳細画面に遷移する。
- 「対戦相手の戦績をついでに確認したい」「あの人と過去何回対戦したか調べたい」という流れが多発しており、対戦詳細へ移動 → 対戦相手名タップ → 一覧へ、と毎回 2 ステップかかっている。
- 一覧側で対戦相手名を直接タップして遷移できるようにすることで、選手探索のフローを短縮する。
- 同時に「自分の対戦の振り返り（メモ閲覧）」もしたいケースがあるため、別導線として対戦詳細への遷移ボタンも明示する。

---

## 2. ユーザーストーリー

### 対象ユーザー
- ログイン中の一般選手（PLAYER ロール）
- メンター（メンティーの対戦一覧を見ている時）

### 利用シナリオ
1. **自分の対戦一覧から対戦相手の戦績を見たい**
   - 「あの人とこれまで何回戦って何勝何敗だっけ？」を確認するため、対戦相手名をタップしてその人の対戦一覧へ。
2. **メンターがメンティーの対戦一覧を見ているとき、メンティーの対戦相手の情報も確認したい**
   - メンティーが対戦した相手の戦績を見るため、対戦相手名をタップ。
3. **過去の対戦を振り返ってメモを読みたい**
   - 自分の対戦一覧で、メモアイコンをタップして対戦詳細画面（メモ・お手付き等）を開く。
4. **メンターがメンティーの対戦詳細を見たい**
   - メンティーの対戦一覧でメモアイコンをタップして詳細画面へ。
5. **選手探索中（他選手の対戦一覧閲覧中）に対戦相手も辿りたい**
   - 他選手の対戦一覧でも対戦相手名タップで遷移可能（チェーン遷移）。ただしメンター関係がない他選手の対戦詳細は閲覧しない。

### 使用デバイス
- スマートフォン専用想定（タップ操作前提、誤タップを避ける設計）

---

## 3. 機能要件

### 3.1 画面仕様

#### 対象画面
- `/matches` （対戦一覧画面、`MatchList.jsx`）

#### 対戦行のレイアウト
現状を維持しつつ、対戦相手名とメモアイコンの **挙動** のみを変更する：

```
┌────────────────────────────────────────────┐
│ 5/22  山田太郎       📝  手2  〇+3        │
│ 5/15  佐藤花子          手0  ×-1          │
│ 5/10  田中一郎       📝       △+0         │
└────────────────────────────────────────────┘
   ↑      ↑           ↑   ↑    ↑
  日付  対戦相手名   メモ 手N  結果
        （リンク化） アイコン
                   （ボタン化）
```

並び順は **現状維持**：日付 → 対戦相手名 → メモアイコン → 手N → 結果

#### タップ動作
| タップ対象 | 遷移先 | 表示条件 |
|---|---|---|
| 対戦相手名（テキスト部分） | `/matches?playerId=<opponentId>` | 常時（ゲスト選手の場合は無効化） |
| メモアイコン（📝） | `/matches/<matchId>` （自分閲覧時）<br>`/matches/<matchId>?playerId=<targetPlayerId>` （メンター閲覧時） | 自分閲覧時 ＋ メンターがメンティー対戦一覧を見ている時のみ表示 |
| その他の行領域（日付・手N・結果） | 反応なし（タップ無効） | — |

**重要な仕様変更点：**
- 現状は「行全体タップ → 対戦詳細へ遷移」だが、新仕様では **行全体のタップハンドラを削除**。タップで反応するのは「対戦相手名」と「メモアイコン」の 2 箇所のみ。
- 他人（非メンティー）の対戦一覧では、メモアイコンが表示されないため、**対戦詳細へ遷移する手段がない**（仕様）。

#### 対戦相手名の表示スタイル
- フォント色を変更（テーマ色 `#4a6b5a`）。下線は付けない。
- フォントウェイト・サイズは現状維持（`text-sm font-medium`）。
- ゲスト選手（`player1Id` または `player2Id` が `null`/`0`）の場合は、現状と同じ通常テキスト色（`#374151`）で表示し、タップしても遷移しない。

#### メモアイコンの表示ルール
| 条件 | 表示 |
|---|---|
| 詳細導線を表示するべき閲覧ケース ＋ メモあり | 濃色アイコン（例: `text-gray-600`） |
| 詳細導線を表示するべき閲覧ケース ＋ メモなし | 薄色アイコン（例: `text-gray-300`） |
| 詳細導線を表示しない閲覧ケース | 非表示 |
| メンター関係 API がフェッチ中（他選手閲覧時） | 非表示 |

- アイコンは現状の `lucide-react` の `StickyNote`（`w-3.5 h-3.5`）をそのまま流用し、`<button>` 要素でラップしてタップ可能にする。
- メモの有無判定は閲覧者から見た自分のメモを参照する：
  - 自分閲覧時: `match.myPersonalNotes` が空でなければ「あり」
  - メンター閲覧時: `match.menteePersonalNotes` が空でなければ「あり」

### 3.2 ビジネスルール

#### 詳細導線（メモアイコン）の表示条件
以下のいずれかを満たす場合のみ表示：
1. **自分閲覧時**: `targetPlayerId === currentPlayer.id`
2. **メンター閲覧時**: `targetPlayerId !== currentPlayer.id` かつ、当該 `targetPlayerId` が `mentorRelationshipAPI.getMyMentees()` のレスポンスに `status === 'ACTIVE'` で含まれる

#### 対戦相手名リンクの遷移条件
- `opponentId` （フロントで計算）が `null` でも `0` でもない場合のみリンク化
- それ以外は通常テキスト（無効）

#### opponentId の計算ロジック
```javascript
const opponentId = match.player1Id === targetPlayerId
  ? match.player2Id
  : match.player1Id;
```
※ `targetPlayerId` は `MatchList` 内で計算済みの「閲覧対象の選手 ID」（自分閲覧時は `currentPlayer.id`、他人閲覧時は `?playerId` クエリ値）。

#### 連鎖遷移時の挙動
- 対戦相手名タップによって新しい `playerId` で同じ `MatchList` 画面に遷移する（URL のみ変更）。
- 統計・対戦一覧・メンター関係チェックが新しい `targetPlayerId` で再フェッチされる。

#### エラーケース
| ケース | 挙動 |
|---|---|
| `opponentId` が取得不能（`null`/`0`） | 対戦相手名はテキスト表示、タップしても遷移しない |
| メンター関係 API 取得失敗 | エラーログを出すが画面表示は継続（詳細導線は出さない=安全側） |
| `getMyMentees()` がロード中 | 詳細導線は非表示で待機 |

#### 既存機能との関係（仕様変更点）
- **行全体タップ → 対戦詳細遷移** は廃止。
  - 自分閲覧時・メンター閲覧時はメモアイコンタップで詳細へ遷移可能。
  - その他の閲覧ケース（一般選手が他人の対戦一覧を見る場合）では、対戦詳細へは遷移できなくなる。
  - これはユーザー要望に基づく仕様変更（プライバシー・利用シナリオ整理の観点）。

---

## 4. 技術設計

### 4.1 API 設計
- **新規 API なし**。既存 API のみ使用。
- 使用する API:
  - `matchAPI.getByPlayerId(targetPlayerId, params)` — 対戦一覧取得（既存）
  - `matchAPI.getStatisticsByRank(targetPlayerId, params)` — 級別統計取得（既存）
  - `playerAPI.getById(targetPlayerId)` — 対象選手名取得（既存）
  - `mentorRelationshipAPI.getMyMentees()` — 自分のメンティー一覧取得（既存）

### 4.2 DB 設計
- **変更なし**。スキーマ改修不要。

### 4.3 フロントエンド設計

#### 対象ファイル
- `karuta-tracker-ui/src/pages/matches/MatchList.jsx`（主要な実装変更）
- `karuta-tracker-ui/src/pages/matches/MatchList.test.jsx`（新規作成）

#### 状態管理（MatchList 内に追加）
```javascript
const [isMentorOfTarget, setIsMentorOfTarget] = useState(false);
const [mentorCheckLoading, setMentorCheckLoading] = useState(true);
```

#### メンター関係チェック（新規 useEffect）
- `targetPlayerId` が `currentPlayer.id` と異なる場合のみ `mentorRelationshipAPI.getMyMentees()` を呼ぶ
- レスポンスに `targetPlayerId` が `status === 'ACTIVE'` で含まれていれば `isMentorOfTarget = true`
- 自分閲覧時は API を呼ばずに即 `mentorCheckLoading = false`

#### 詳細導線表示判定
```javascript
const showDetailButton = !isOtherPlayer || isMentorOfTarget;
```

#### 行の描画ロジック（疑似コード）
```jsx
{filteredMatches.map((match) => {
  const opponentId = match.player1Id === targetPlayerId
    ? match.player2Id
    : match.player1Id;
  const opponentLinkable = opponentId && opponentId !== 0;

  const hasNote = isOtherPlayer
    ? !!match.menteePersonalNotes
    : !!match.myPersonalNotes;
  const otetsukiCount = isOtherPlayer ? match.menteeOtetsukiCount : match.myOtetsukiCount;

  return (
    <div key={match.id} className="flex items-center px-4 py-2 ...">
      <span className="text-xs text-[#9ca3af] w-12 flex-shrink-0">{formatDate(match.matchDate)}</span>

      {opponentLinkable ? (
        <button
          onClick={() => navigate(`/matches?playerId=${opponentId}`)}
          className="flex-1 min-w-0 text-sm font-medium text-[#4a6b5a] text-left truncate"
        >
          {match.opponentName}
        </button>
      ) : (
        <span className="flex-1 min-w-0 text-sm font-medium text-[#374151] text-left truncate">
          {match.opponentName}
        </span>
      )}

      {showDetailButton && !mentorCheckLoading && (
        <button
          onClick={() => navigate(
            `/matches/${match.id}${isOtherPlayer ? '?playerId=' + targetPlayerId : ''}`
          )}
          aria-label="対戦詳細を見る"
          className={`flex-shrink-0 ml-1 p-1 ${hasNote ? 'text-gray-600' : 'text-gray-300'}`}
        >
          <StickyNote className="w-3.5 h-3.5" />
        </button>
      )}

      {otetsukiCount != null && (
        <span className="text-xs text-[#9ca3af] flex-shrink-0 ml-1">手{otetsukiCount}</span>
      )}

      <span className={`text-sm font-bold flex-shrink-0 ml-2 ${getResultColor(match.result)}`}>
        {getResultDisplay(match.result, match.scoreDifference)}
      </span>
    </div>
  );
})}
```

#### コンポーネント構造
- 新規コンポーネント追加なし。`MatchList.jsx` 内に閉じる。

### 4.4 バックエンド設計
- **変更なし**。

---

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル
| ファイル | 変更内容 |
|---|---|
| `karuta-tracker-ui/src/pages/matches/MatchList.jsx` | 対戦行のタップ挙動を変更（相手名リンク化・メモアイコン詳細ボタン化・行全体タップ廃止）、メンター関係チェック追加 |
| `karuta-tracker-ui/src/pages/matches/MatchList.test.jsx` | 新規作成（Vitest + Testing Library） |
| `docs/SPECIFICATION.md` | 対戦一覧画面の仕様セクション更新 |
| `docs/SCREEN_LIST.md` | `/matches` 画面の説明更新 |

### 5.2 既存機能への影響
| 機能 | 影響 |
|---|---|
| 他選手の対戦一覧からの詳細画面遷移 | **不可になる**（仕様変更）。メンター関係のないユーザーは対戦詳細ページを開けなくなる |
| 自分の対戦一覧 → 対戦詳細遷移 | 行全体タップ廃止、メモアイコンタップに変更 |
| 対戦詳細画面（MatchDetail）の対戦相手名リンク | 影響なし（既存実装そのまま） |
| 級別統計表示・期間フィルタ・選手検索など | 影響なし |
| バックエンド API | 影響なし |
| DB スキーマ | 影響なし |

### 5.3 影響を受けない箇所
- `MatchDto`、`MatchService`、`MatchController`：変更なし
- `MatchDetail.jsx`：変更なし
- 他のページ（`PlayerList`、`PlayerDetail` 等）：変更なし
- ルーティング（`App.jsx`）：変更なし

---

## 6. 設計判断の根拠

### 6.1 opponentId をフロントで計算する理由
- `MatchDto` には既に `player1Id`/`player2Id` が含まれており、`MatchDetail.jsx` でも同じパターン（フロントで計算）が確立している
- バックエンド改修不要でシンプル
- 「opponent」の概念は閲覧者（targetPlayerId）に依存するため、バックエンドで一意に決められない

### 6.2 メモアイコン（StickyNote）を詳細ボタンとして再利用する理由
- 既存の「メモ表示用アイコン」を兼用することで、行のレイアウト変更を最小化（スマホでの情報密度を維持）
- 詳細画面で表示される主な追加情報がメモ・お手付きであり、メモアイコンが「詳細を見る」のメタファとして適切
- メモ有無を濃淡で表現することで、メモあり試合の発見性も維持

### 6.3 行全体タップによる詳細遷移を廃止する理由
- ユーザーが「対戦詳細画面は自分とメンティーのもののみで十分」と明示
- 対戦相手名と詳細ボタンの 2 つのタップ対象を明確に分離することで誤タップを防ぐ
- 対戦相手名以外の領域はタップしても何も起きない方が、ユーザーの予期と一致しやすい

### 6.4 対戦相手名のスタイルを「色のみ・下線なし」にした理由
- ユーザーの好み（情報密度の高い一覧画面では下線を控えめにしたい）
- 行内の他要素との視覚的バランスを重視
- 「色違い」だけでも十分にリンクと認識できる（テーマ色 #4a6b5a を採用）

### 6.5 メンター関係チェックを並行フェッチして取得完了まで詳細ボタンを非表示にする理由
- 詳細ボタンが「あったり消えたり」する瞬間のチラつきを避けつつ、ボタンを誤って表示しないため
- 一覧表示自体は API レスポンスがあれば即出せるので、UX を損なわない
- 自分閲覧時は API を呼ばないため待ち時間ゼロ
