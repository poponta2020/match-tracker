---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, 技術設計, 影響範囲]
next_section: null
---
# 取り札記録 要件定義書

## 1. 概要

### 目的
個人の試合結果入力（MatchForm）で、その試合の**出札50枚を決まり字チップとして表示**し、各札を「どこで（敵陣/自陣 × 左/右 × 上/中/下段）取ったか・取られたか」を配置して記録できるようにする。あわせて、お手付きを**種類別に詳細記録**できるようにする。振り返り・（将来の）傾向分析の素材とする。

### 背景・動機
- 既存の「お手付き記録」機能（`match_personal_notes.otetsuki_count`）は**回数のみ**で、内容の振り返りができない。
- 「どの位置で札を取れているか／取られているか」「どんなお手付きをしたか」を可視化する土台がない。
- 出札50枚（札組）は対戦組み合わせ機能の**札ルール**（[cardRules.js](../../../karuta-tracker-ui/src/pages/pairings/cardRules.js)）から決定論的に導出できるため、その試合の出札一覧を自動表示できる。

### スコープ（今回）
- **記録機能のみ**（入力・保存・編集）。集計・可視化（ヒートマップ等）は将来の別機能とする。
- 取り札配置・お手付き詳細の入力は**任意**（従来どおり勝敗・枚数差・お手付き回数だけでも保存可）。

## 2. ユーザーストーリー

### 対象ユーザー
- 各プレイヤー（PLAYER / ADMIN）が、自分自身の試合について記録する。

### ユーザーの目的
- 自分がどの位置で札を取れた／取られたかを、試合後に振り返りたい。
- お手付きを種類（ひっかけ／暗記間違え／聞き間違い／その他）別に具体的に記録したい。

### 利用シナリオ
1. プレイヤーが `/matches/new`（または `/matches/:id/edit`）で結果を入力する。
2. 対象試合の**出札50枚が決まり字チップ**として表示される（`(その日, その試合番号)` の札ルールから導出）。
3. 覚えている札を、12エリア（敵左/敵右/自左/自右 × 上/中/下段）の「取った」「取られた」へ配置。覚えていない札は**不明**エリアに残す。
4. お手付き回数を入力すると、回数分の**詳細入力枠**が現れ、種類と種類別の詳細を記録する。
5. 結果保存と同時に、取り札配置・お手付き詳細が**自分のプライベートデータ**として保存される（相手には非公開）。
6. 後から編集でき、保存済みの配置・詳細が復元される。

## 3. 機能要件

### 3.1 画面仕様

**画面レイアウトの視覚設計は design-spec.md に委譲する**（本書では再記述しない）。ロジック・データ・遷移のみ記載する。

#### 記録の載せ場所
- 既存の [MatchForm.jsx](../../../karuta-tracker-ui/src/pages/matches/MatchForm.jsx)（`/matches/new`, `/matches/:id/edit`）に組み込む。
- 既存の「結果・枚数差・お手付き回数・メモ」はそのまま。**お手付き回数入力は残す（併存）**。
- 取り札配置UI・お手付き詳細UIは、**「取り札・お手付きを記録」の折りたたみブロック（任意・初期は閉じ）**として追加。展開時のみ表示（=従来の素早い入力フローを妨げない）。
- 視覚設計（畳表現・縦札・左右分割・敵陣180°回転・折りたたみ）は [design-spec.md](./design-spec.md) を参照（本書では再記述しない）。決まり字は [kimariji-master.md](./kimariji-master.md)（最大4文字・共札は「共通字・区別字」記法）。

#### 出札チップの表示
- 対象試合 `(matchDate, matchNumber)` の**札ルール**から出札50枚を導出し、各札を**決まり字のみ**のチップとして表示する。
- 決まり字は**100枚の標準決まり字（静的）**を用いる（場の構成に応じた動的短縮はしない）。
- チップの初期位置は「不明」エリア。

#### 取り札の配置（グリッド）
- 配置先は次の 12エリア × {取った / 取られた} ＋ 不明：
  - 敵陣左 / 敵陣右 / 自陣左 / 自陣右（4象限）× 上段 / 中段 / 下段（3段）＝ 12エリア
  - 各エリアに「取った」「取られた」の別
  - どのエリアにも置かない札は「不明」
- 敵陣/自陣は**記録者（自分）視点**。
- 各札は「不明」か、いずれか1つの（象限・段・取/取られ）に属する（重複不可）。

#### お手付き詳細
- お手付き回数 `N`（既存 `otetsuki_count`）を入力すると、**最大 N 件**の詳細入力枠を表示する。
- 各枠でまず**種類**を選ぶ（必須）：ひっかけ / 暗記間違え / 聞き間違い / その他。
- 種類ごとの追加項目：

  | 種類 | 追加項目 |
  |---|---|
  | ひっかけ | 払おうとした上段位置：自陣右上 / 自陣左上 / 敵陣右上 / 敵陣左上（4択） |
  | 暗記間違え | 方向（2択）：①敵陣に送った札を自陣で触った ／ ②自陣に送られた札を敵陣で触った |
  | 聞き間違い | 読まれた札（1枚）＋ 触った札（1枚）。**選択元は100枚全部**（読まれた札は空札の可能性があるため） |
  | その他 | 自由記述 |

- 対象札の記録：ひっかけ・暗記間違えでは「どの札か」は**記録しない**（位置・方向のみ）。

### 3.2 ビジネスルール
- 取り札配置・お手付き詳細は**入力者のプライベートデータ**。他プレイヤーには一切表示しない（既存 `match_personal_notes` と同方針）。
- 配置は任意。未配置の札は「不明」（＝配置レコードなし）として扱う。
- お手付き詳細も任意。回数分の枠を出すが、未入力の枠は保存しない。回数と詳細件数の厳密一致は**強制しない**。
- **不明プールの配置目安枚数（母数）＝ `50 − 枚数差`。** 実際に取られた/取った札の総数（場に残った札は取られていないため配置対象外）。例: 7枚差 → 43枚。
  - 「あと何枚置けば完了か」を示すガイド。プール見出しに `残り X / (50−枚数差)枚` を表示し、枚数差変更で母数を再計算する。
  - **指導試合・枚数差未入力時は母数を 50 にフォールバック**（または非表示）。
  - あくまで目安で、50枚すべてのチップは操作可能（配置枚数のハード制限はしない）。placed が母数超過時は残数を0でクランプ。
- 取り札配置数と枚数差／お手付き回数の**整合チェックは行わない**（配置枚数は上記の「目安」のみ。best-effort の振り返りメモ）。
- 結果の保存と同一トランザクションで、取り札配置・お手付き詳細を upsert する。

### 3.3 エラーケース・境界条件
- 対象試合の札ルールが導出できない（対象日の練習セッション/試合数が取得できない等）：出札チップ表示を省略し、結果入力自体は従来どおり可能にする（配置UIは非表示 or 空表示）。
- お手付き種類が「その他」で自由記述が空：その枠は未入力扱いで保存しない（バリデーションエラーにはしない）。
- 同一札を複数エリアへ置く操作は UI 側で1箇所に制約する（後勝ち）。

## 4. 技術設計

### 4.1 前提：札組（出札50枚）の権威あるソース化 ★重要

現状、札ルールは**フロント側で `(日付, nonce)` から決定論生成**され、`nonce`（再生成カウンタ）は**端末ごとの localStorage** に保存される（[cardRules.js](../../../karuta-tracker-ui/src/pages/pairings/cardRules.js) の `loadNonce/saveNonce`）。このままでは、記録者の端末とペアリング生成端末で `nonce` が食い違い、表示される50枚がずれる恐れがある。

**対応方針：`nonce` を日付単位でバックエンド共有する。**
- 新テーブル `card_rule_nonce`（`session_date` をキーに `nonce` を保持）を追加。
- 記録画面・「札ルール一覧」画面ともに、`nonce` を**DBから取得**して札ルールを導出する。
- 「札を再生成」時は DB の `nonce` を更新する。
- 既存 localStorage nonce は DB 優先へ移行（後方互換：DB 未登録日は既定 0）。
- これにより「端末間で札ルールが不一致」という**既存の潜在バグも解消**する。

#### 札ルール→出札50枚の展開
- `type` 別に札番号（`01`〜`99`, `00`=100）を展開：
  - `ones`（一の位 5桁）：一の位が該当の札（=50枚）
  - `tens`（十の位 5桁）：十の位が該当の札（=50枚）
  - `nuki`（3桁 + removedCard）：一の位 or 十の位が該当（=51枚）から removedCard を除外（=50枚）
- 展開ロジックは `cardRules.js` に純粋関数として追加（テスト可能化）。

### 4.2 API設計

#### 札ルール nonce
- **GET `/api/card-rule-nonce?date=YYYY-MM-DD`** → `{ date, nonce }`（未登録は `nonce: 0`）
- **PUT `/api/card-rule-nonce`** `{ date, nonce }` → 更新（「札を再生成」時に使用。権限は既存のペアリング操作と同等）

#### 取り札配置・お手付き詳細（試合保存と一体化）
既存の詳細版 Match 作成/更新 API を拡張する（`personalNotes`/`otetsukiCount` と同じ考え方）。

- **POST `/api/matches/detailed`（拡張）** / **PUT `/api/matches/{id}/detailed`（拡張）**
  - リクエストに追加：
    ```json
    {
      "cardPlacements": [
        { "cardNo": 17, "takenBy": "SELF", "field": "ENEMY", "side": "LEFT", "tier": "TOP" }
      ],
      "otetsukiDetails": [
        { "seq": 1, "type": "HIKKAKE", "hikkakeTarget": "OWN_RIGHT_TOP" },
        { "seq": 2, "type": "MISHEARING", "mishearingReadCardNo": 22, "mishearingTouchedCardNo": 12 }
      ]
    }
    ```
  - 認証ユーザー（= player_id）のレコードとして upsert（全置換：送られた配列で当該 (match_id, player_id) を作り直す）。
- **簡易版 POST `/api/matches` / PUT `/api/matches/{id}`**：未登録相手の簡易フローでも同フィールドを受け付け、可能なら保存する（詳細は実装手順書で確定）。

#### 取得（編集時の復元）
- 既存 Match 取得系レスポンスに、リクエストユーザー自身の分を追加：
  ```json
  { "myCardPlacements": [ ... ], "myOtetsukiDetails": [ ... ] }
  ```
  （`my` プレフィックスで「自分自身のデータ」を明示。既存 `myPersonalNotes`/`myOtetsukiCount` に倣う）

### 4.3 DB設計

#### 新規テーブル: `card_rule_nonce`
| カラム | 型 | NULL | 既定 | 説明 |
|---|---|---|---|---|
| session_date | DATE | NO | - | PK。対象日 |
| nonce | INT | NO | 0 | 札ルール再生成カウンタ |
| updated_at | TIMESTAMP | NO | CURRENT_TIMESTAMP | 更新日時 |

#### 新規テーブル: `match_card_placements`
| カラム | 型 | NULL | 説明 |
|---|---|---|---|
| id | BIGINT | NO | PK |
| match_id | BIGINT | NO | FK → matches(id) |
| player_id | BIGINT | NO | FK → players(id)（記録者） |
| card_no | SMALLINT | NO | 札番号 1〜100 |
| taken_by | VARCHAR | NO | `SELF` / `OPPONENT` |
| field | VARCHAR | NO | `ENEMY` / `OWN` |
| side | VARCHAR | NO | `LEFT` / `RIGHT` |
| tier | VARCHAR | NO | `TOP` / `MIDDLE` / `BOTTOM` |
| created_at / updated_at | TIMESTAMP | NO | - |

- UNIQUE: `(match_id, player_id, card_no)`
- INDEX: `(player_id, match_id)`
- 「不明」の札は**行を持たない**（配置レコードの有無＝配置済みか否か）。

#### 新規テーブル: `match_otetsuki_details`
| カラム | 型 | NULL | 説明 |
|---|---|---|---|
| id | BIGINT | NO | PK |
| match_id | BIGINT | NO | FK → matches(id) |
| player_id | BIGINT | NO | FK → players(id)（記録者） |
| seq | INT | NO | 表示順（1..N） |
| otetsuki_type | VARCHAR | NO | `HIKKAKE` / `ANKI_MISS` / `MISHEARING` / `OTHER` |
| hikkake_target | VARCHAR | YES | `OWN_RIGHT_TOP` / `OWN_LEFT_TOP` / `ENEMY_RIGHT_TOP` / `ENEMY_LEFT_TOP` |
| anki_direction | VARCHAR | YES | `SENT_TO_ENEMY_TOUCHED_OWN` / `RECEIVED_FROM_ENEMY_TOUCHED_ENEMY` |
| mishearing_read_card_no | SMALLINT | YES | 読まれた札（1〜100） |
| mishearing_touched_card_no | SMALLINT | YES | 触った札（1〜100） |
| other_text | TEXT | YES | その他の自由記述 |
| created_at / updated_at | TIMESTAMP | NO | - |

- UNIQUE: `(match_id, player_id, seq)`
- INDEX: `(player_id, match_id)`

> **本番DB適用が必要**（`database/*.sql` を追加。CLAUDE.md「DBマイグレーション適用ルール」に従い、entity 変更と同一 PR＋本番 psql 適用）。

### 4.4 フロントエンド設計

#### 決まり字マスター（新規・静的定数）
- [kimariji-master.md](./kimariji-master.md)（確定100枚）を `karuta-tracker-ui/src/data/kimariji.js` の定数に落とす（`{ [cardNo]: 決まり字 }`）。
- 決まり字は**最大4文字**。共札は「共通字・区別字」記法（わた・や(011)/わた・こ(076)、きみ・は(015)/きみ・お(050)、よの・よ(083)/よの・は(093)、あさぼあ(031)/あさぼう(064)＝・なし4文字）。
- 参照ファイルの誤り補正済み: 041=こひ、068=こころに、082=おも。
- チップ表示は決まり字のみ（縦書き）。

#### コンポーネント
- **MatchForm.jsx**：札ルール nonce を取得 → 出札50枚導出 → 取り札配置UI・お手付き詳細UIを追加。保存時に `cardPlacements` / `otetsukiDetails` を送信。編集時は `myCardPlacements` / `myOtetsukiDetails` を復元。
- 取り札配置UI・お手付き詳細UIは**新規コンポーネントに分離**（構成は design-spec）。
- **cardRules.js**：札ルール→50枚展開の純粋関数を追加。`loadNonce/saveNonce` を API 連携へ拡張（DB 優先）。
- **PairingSummary.jsx**：nonce を DB 経由で読み書きするよう変更（「札を再生成」を DB 更新に）。

#### 状態管理
- `formData` に `cardPlacements`（`{cardNo→{takenBy,field,side,tier}}` 相当）と `otetsukiDetails`（配列）を追加。

### 4.5 バックエンド設計

#### 新規
- Entity：`CardRuleNonce`, `MatchCardPlacement`, `MatchOtetsukiDetail`
- Repository：各リポジトリ
- Controller/Service：`CardRuleNonceController`（GET/PUT）、`MatchService` に配置・お手付き詳細の upsert / 取得を追加
- DTO：`CardPlacementDto`, `OtetsukiDetailDto`、Match 系 DTO に `myCardPlacements`/`myOtetsukiDetails` 追加、Match 作成/更新リクエストに配列追加

#### 認可
- 取り札配置・お手付き詳細は**記録者本人のみ**読み書き可（`player_id` == 認証ユーザー）。他人分は返さない。

## 5. 影響範囲

### 変更が必要な既存ファイル（主なもの）
- フロント：`MatchForm.jsx`（記録UI・保存・復元）、`pairings/cardRules.js`（展開関数・nonce API化）、`pairings/PairingSummary.jsx`（nonce DB化）、`api/`（matches・新規 card-rule-nonce クライアント）
- バックエンド：`MatchController` / `MatchService` / Match 系 DTO・リクエスト、新規 Controller/Service/Entity/Repository
- DB：`database/` に新規 SQL 3テーブル（**本番適用要**）

### 既存機能への影響
- **お手付き回数（既存）**：併存。今回の詳細記録は追加。回数は従来どおり `otetsuki_count` に保存。
- **札ルール永続化（pairing-card-rule-persistence）**：nonce の保存を localStorage → DB へ移行するため、この機能の挙動に影響。**同日内の再生成固定という要件は維持**しつつ、保存先を DB に変更（端末間一致という改善を伴う）。※要件整合を実装時に確認。
- **BulkResultInput（管理者一括入力）**：取り札配置・お手付き詳細は入力対象外（従来どおり）。
- **統計機能**：今回は集計しない（将来の別機能）。

## 6. 設計判断の根拠
- **nonce を DB 共有**：記録は各自の端末で行うため、端末ローカル nonce では50枚がずれる。日付単位の nonce（整数1つ）を DB 化するのが最小コストで堅牢、かつ既存の端末間不一致バグも解消。
- **専用テーブル（正規化）**：既存要件に「将来お手付きの集計・分析予定」とあり、ヒートマップ等の集計に有利。JSON 格納より将来の分析コストが低い。
- **決まり字は静的**：チップは識別ラベル用途で足り、動的短縮は実装が重い。標準決まり字で十分。
- **12エリア×取/取られ＋不明**：ユーザーの取り札振り返り粒度に一致（段別・敵自左右別）。
- **記録は任意・整合チェックなし**：私的な振り返りメモであり、入力ハードルを上げない。
- **MatchForm 併設**：結果入力と同一導線で完結させ、別画面往復を避ける（既存 `personalNotes`/`otetsukiCount` と同じ保存導線）。

## デザインへの宿題（→ /design-screen 取り札記録）※すべて解決済み
[design-spec.md](./design-spec.md)（status: locked）で確定：畳表現・縦書き札チップ（最大4文字）・左右分割（取った左/取られた右）・敵陣180°回転・不明プールは陣の間・お手付き種類別の動的フォーム・100枚ピッカー・**取り札＋お手付きは折りたたみ（任意・初期閉じ）**。決まり字は [kimariji-master.md](./kimariji-master.md)。→ 収束ゲート通過。
