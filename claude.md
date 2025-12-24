# Claude Code ルール

このファイルには、このプロジェクトでClaude Codeが守るべきルールを記載します。

## 🔴 最重要ルール：変数・メソッド名管理

**新たな変数やメソッドを作成・変更したら、必ずこのファイルを更新すること！**

バグの多くは命名の不一致から発生するため、このファイルで一元管理します。

---

## 📋 変数名・メソッド名マスター定義

### 1. エンティティ（Entity）フィールド名

#### Match（試合エンティティ）
| フィールド名 | 型 | 説明 | DB列名 |
|------------|---|------|--------|
| `id` | Long | 試合ID | `id` |
| `matchDate` | LocalDate | 対戦日 | `match_date` |
| `matchNumber` | Integer | その日の第何試合目か | `match_number` |
| `player1Id` | Long | 選手1のID | `player1_id` |
| `player2Id` | Long | 選手2のID | `player2_id` |
| `winnerId` | Long | 勝者のID | `winner_id` |
| `scoreDifference` | Integer | 枚数差（1～50） | `score_difference` |
| `opponentName` | String | 対戦相手名（未登録選手用） | `opponent_name` |
| `notes` | String | コメント | `notes` |
| `createdBy` | Long | 作成者のID | `created_by` |
| `updatedBy` | Long | 最終更新者のID | `updated_by` |
| `createdAt` | LocalDateTime | 作成日時 | `created_at` |
| `updatedAt` | LocalDateTime | 更新日時 | `updated_at` |

**重要メソッド:**
- `isPlayer1Winner()` - player1が勝者かを判定
- `isPlayer2Winner()` - player2が勝者かを判定
- `ensurePlayer1LessThanPlayer2()` - player1_id < player2_idを保証

#### Player（選手エンティティ）
| フィールド名 | 型 | 説明 | DB列名 |
|------------|---|------|--------|
| `id` | Long | 選手ID | `id` |
| `name` | String | 選手名（ログイン用） | `name` |
| `password` | String | パスワード（BCryptハッシュ） | `password` |
| `gender` | Gender | 性別（男性/女性/その他） | `gender` |
| `dominantHand` | DominantHand | 利き手（右/左/両） | `dominant_hand` |
| `danRank` | DanRank | 段位 | `dan_rank` |
| `kyuRank` | KyuRank | 級位 | `kyu_rank` |
| `karutaClub` | String | 所属かるた会 | `karuta_club` |
| `remarks` | String | 備考 | `remarks` |
| `role` | Role | ロール（SUPER_ADMIN/ADMIN/PLAYER） | `role` |
| `deletedAt` | LocalDateTime | 削除日時（論理削除） | `deleted_at` |
| `createdAt` | LocalDateTime | 作成日時 | `created_at` |
| `updatedAt` | LocalDateTime | 更新日時 | `updated_at` |

**重要メソッド:**
- `isDeleted()` - 論理削除されているかを判定

---

### 2. DTO（Data Transfer Object）フィールド名

#### MatchDto
| フィールド名 | 型 | 説明 |
|------------|---|------|
| `id` | Long | 試合ID |
| `matchDate` | LocalDate | 対戦日 |
| `matchNumber` | Integer | 試合番号 |
| `player1Id` | Long | 選手1のID |
| `player1Name` | String | 選手1の名前 |
| `player2Id` | Long | 選手2のID |
| `player2Name` | String | 選手2の名前 |
| `winnerId` | Long | 勝者のID |
| `winnerName` | String | 勝者の名前 |
| `scoreDifference` | Integer | 枚数差 |
| `opponentName` | String | 対戦相手名（簡易表示用） |
| `result` | String | 結果（勝ち/負け/引き分け） |
| `notes` | String | コメント |
| `createdAt` | LocalDateTime | 作成日時 |
| `updatedAt` | LocalDateTime | 更新日時 |
| `createdBy` | Long | 作成者のID |
| `updatedBy` | Long | 最終更新者のID |

**重要メソッド:**
- `fromEntity(Match match)` - EntityからDTOへ変換
- `isPlayer1Winner()` - player1が勝者かを判定
- `isPlayer2Winner()` - player2が勝者かを判定

#### PlayerDto
| フィールド名 | 型 | 説明 |
|------------|---|------|
| `id` | Long | 選手ID |
| `name` | String | 選手名 |
| `gender` | Player.Gender | 性別 |
| `dominantHand` | Player.DominantHand | 利き手 |
| `danRank` | Player.DanRank | 段位 |
| `kyuRank` | Player.KyuRank | 級位 |
| `karutaClub` | String | 所属かるた会 |
| `remarks` | String | 備考 |
| `role` | Player.Role | ロール |
| `createdAt` | LocalDateTime | 作成日時 |
| `updatedAt` | LocalDateTime | 更新日時 |
| `deletedAt` | LocalDateTime | 削除日時 |

**重要メソッド:**
- `fromEntity(Player player)` - EntityからDTOへ変換
- `isActive()` - アクティブ（削除されていない）かを判定

---

### 3. フロントエンド API命名規則

#### API オブジェクト名
| API名 | 説明 | ファイル |
|------|------|---------|
| `playerAPI` | 選手関連API | `src/api/players.js` |
| `matchAPI` | 試合関連API | `src/api/matches.js` |
| `practiceAPI` | 練習関連API | `src/api/practices.js` |
| `pairingAPI` | 組み合わせ関連API | `src/api/pairings.js` |
| `venueAPI` | 会場関連API | `src/api/venues.js` |
| `apiClient` | 共通Axiosクライアント | `src/api/client.js` |

#### matchAPI メソッド一覧
| メソッド名 | 説明 | HTTPメソッド | エンドポイント |
|----------|------|------------|--------------|
| `getAll()` | 全試合取得 | GET | `/api/matches` |
| `getById(id)` | ID指定取得 | GET | `/api/matches/{id}` |
| `create(data)` | 新規作成 | POST | `/api/matches` |
| `update(id, data)` | 更新 | PUT | `/api/matches/{id}` |
| `delete(id)` | 削除 | DELETE | `/api/matches/{id}` |

#### playerAPI メソッド一覧
| メソッド名 | 説明 | HTTPメソッド | エンドポイント |
|----------|------|------------|--------------|
| `getAll()` | 全選手取得 | GET | `/api/players` |
| `getById(id)` | ID指定取得 | GET | `/api/players/{id}` |
| `create(data)` | 新規作成 | POST | `/api/players` |
| `update(id, data)` | 更新 | PUT | `/api/players/{id}` |
| `delete(id)` | 削除 | DELETE | `/api/players/{id}` |
| `login(credentials)` | ログイン | POST | `/api/players/login` |

---

### 4. フロントエンド State変数名

#### MatchForm.jsx の formData
| フィールド名 | 型 | 説明 | 初期値 |
|------------|---|------|--------|
| `matchDate` | string | 対戦日（YYYY-MM-DD） | 今日の日付 |
| `opponentName` | string | 対戦相手名 | 空文字 |
| `result` | string | 結果（勝ち/負け） | '勝ち' |
| `scoreDifference` | number | 枚数差 | 0 |
| `matchNumber` | number | 試合番号 | 1 |
| `notes` | string | メモ | 空文字 |

---

### 5. バックエンド Repository メソッド命名規則

#### MatchRepository
| メソッド名 | 説明 | 戻り値 |
|----------|------|--------|
| `findByPlayerIdAndMatchDateAndMatchNumber(playerId, matchDate, matchNumber)` | 選手・日付・試合番号で検索 | Match |
| `existsByPlayerIdAndMatchDateAndMatchNumber(playerId, matchDate, matchNumber)` | 重複チェック | boolean |
| `findByMatchDate(matchDate)` | 日付で検索 | List\<Match\> |
| `findByPlayer1IdOrPlayer2Id(player1Id, player2Id)` | 選手IDで検索 | List\<Match\> |

#### PlayerRepository
| メソッド名 | 説明 | 戻り値 |
|----------|------|--------|
| `findByName(name)` | 名前で検索 | Optional\<Player\> |
| `findByNameAndDeletedAtIsNull(name)` | アクティブな選手を名前で検索 | Optional\<Player\> |
| `findAllByDeletedAtIsNull()` | アクティブな選手を全取得 | List\<Player\> |

---

### 6. バックエンド Service メソッド命名規則

#### MatchService
| メソッド名 | 説明 | パラメータ | 戻り値 |
|----------|------|----------|--------|
| `createMatchSimple(request)` | 簡易試合作成 | MatchSimpleCreateRequest | MatchDto |
| `updateMatchSimple(id, request)` | 簡易試合更新 | Long, MatchSimpleCreateRequest | MatchDto |
| `updateMatch(id, winnerId, scoreDifference, updatedBy)` | 詳細試合更新 | Long, Long, Integer, Long | MatchDto |
| `getMatchById(id)` | ID指定取得 | Long | MatchDto |
| `getAllMatches()` | 全試合取得 | - | List\<MatchDto\> |
| `deleteMatch(id)` | 試合削除 | Long | void |
| `enrichMatchWithPlayerNames(match)` | 選手名を付加 | Match | MatchDto |

---

### 7. 列挙型（Enum）値の定義

#### Player.Gender（性別）
- `男性`
- `女性`
- `その他`

#### Player.DominantHand（利き手）
- `右`
- `左`
- `両`

#### Player.DanRank（段位）
- `無段`, `初段`, `弐段`, `参段`, `四段`, `五段`, `六段`, `七段`, `八段`

#### Player.KyuRank（級位）
- `E級`, `D級`, `C級`, `B級`, `A級`

#### Player.Role（ロール）
- `SUPER_ADMIN` - 最上位管理者：全機能
- `ADMIN` - 管理者：練習日管理、組み合わせ作成、基本機能
- `PLAYER` - 一般選手：基本機能のみ

---

## 🔒 命名ルール

### バックエンド（Java）
1. **Entity/DTOフィールド**: `camelCase`（例：`matchDate`, `player1Id`）
2. **DB列名**: `snake_case`（例：`match_date`, `player1_id`）
3. **メソッド名**: `camelCase`の動詞始まり（例：`createMatch`, `isPlayer1Winner`）
4. **クラス名**: `PascalCase`（例：`MatchService`, `PlayerDto`）
5. **定数**: `UPPER_SNAKE_CASE`（例：`MAX_SCORE_DIFFERENCE`）

### フロントエンド（JavaScript/React）
1. **変数名**: `camelCase`（例：`matchDate`, `currentPlayer`）
2. **コンポーネント名**: `PascalCase`（例：`MatchForm`, `PlayerList`）
3. **API関数**: `camelCase`の動詞始まり（例：`getAll`, `createMatch`）
4. **State変数**: `camelCase`（例：`formData`, `isLoading`）
5. **定数**: `UPPER_SNAKE_CASE`（例：`API_BASE_URL`）

---

## ✅ 変数・メソッド作成時のチェックリスト

新しい変数やメソッドを作成したら、以下を必ず実施：

- [ ] この`claude.md`ファイルに追記
- [ ] フロントエンド⇔バックエンド間で同じ概念は同じ名前を使用
- [ ] Entity/DTO/API間でフィールド名の整合性を確認
- [ ] DB列名とJavaフィールド名の対応を明記
- [ ] 既存の命名規則に従っているか確認

---

## 🎯 コーディング規約

### バックエンド（Spring Boot）
- Lombokアノテーション（`@Data`, `@Builder`, `@Getter`, `@Setter`）を積極的に使用
- `@PrePersist`, `@PreUpdate`で日時自動設定
- 論理削除は`deletedAt`フィールドで管理
- トランザクション境界は`@Transactional`を明示

### フロントエンド（React）
- 関数コンポーネントとHooksを使用
- `useState`, `useEffect`で状態管理
- API呼び出しは`try-catch`でエラーハンドリング
- ローディング状態（`loading`）とエラー状態（`error`）を管理

---

## 📦 コミットルール

### コミットメッセージ形式
```
<type>: <subject>

<body>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Type一覧
- `feat:` 新機能
- `fix:` バグ修正
- `docs:` ドキュメント
- `refactor:` リファクタリング
- `test:` テスト追加
- `style:` コードフォーマット
- `chore:` ビルド・設定変更

### 重要な注意
- **変数名やメソッド名を変更した場合は、必ずこの`claude.md`も一緒にコミット**
- コミット前に`claude.md`が最新かチェック

---

## 🚨 禁止事項

1. **既存の変数名・メソッド名を勝手に変更しない**
   - 変更が必要な場合は、このファイルを更新してから実施
   - フロントエンド⇔バックエンドの両方を同時に変更

2. **類似の概念に異なる名前を付けない**
   - 例：`matchDate`と`gameDate`を混在させない
   - 例：`playerId`と`userId`を混在させない

3. **DB列名とJavaフィールド名の対応を崩さない**
   - `match_date` ⇔ `matchDate`の対応を守る
