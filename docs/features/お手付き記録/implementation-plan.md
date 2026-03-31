---
status: completed
---
# お手付き記録 実装手順書

## 実装タスク

### タスク1: DBスキーマ変更（新テーブル作成 + notesカラム削除）
- [x] 完了
- **概要:** `match_personal_notes` テーブルを作成し、`matches.notes` カラムを削除するマイグレーションSQLを作成・実行する。
- **変更対象ファイル:**
  - `database/add_match_personal_notes.sql` — 新規作成。CREATE TABLE + DROP COLUMN
- **依存タスク:** なし
- **対応Issue:** #170

#### 詳細

```sql
CREATE TABLE match_personal_notes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  match_id BIGINT NOT NULL,
  player_id BIGINT NOT NULL,
  notes TEXT NULL,
  otetsuki_count INT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
  UNIQUE KEY uq_match_personal_notes (match_id, player_id),
  INDEX idx_match_personal_notes_player (player_id, match_id),
  CONSTRAINT chk_otetsuki_count CHECK (otetsuki_count >= 0 AND otetsuki_count <= 20)
);

ALTER TABLE matches DROP COLUMN notes;
```

---

### タスク2: バックエンド — Entity / Repository 作成
- [x] 完了
- **概要:** `MatchPersonalNote` エンティティと `MatchPersonalNoteRepository` を作成する。`Match` エンティティから `notes` フィールドを削除する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchPersonalNote.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Match.java` — `notes` フィールド削除
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchPersonalNoteRepository.java` — 新規作成
- **依存タスク:** タスク1 (#170)
- **対応Issue:** #172

#### Entity設計

```java
@Entity
@Table(name = "match_personal_notes")
public class MatchPersonalNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "otetsuki_count")
    private Integer otetsukiCount;  // null許容

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### Repository設計

```java
public interface MatchPersonalNoteRepository extends JpaRepository<MatchPersonalNote, Long> {
    Optional<MatchPersonalNote> findByMatchIdAndPlayerId(Long matchId, Long playerId);
    List<MatchPersonalNote> findByPlayerIdAndMatchIdIn(Long playerId, List<Long> matchIds);
}
```

---

### タスク3: バックエンド — DTO変更
- [x] 完了
- **概要:** 既存DTOから `notes` を削除し、個人メモ・お手付き用フィールドを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java` — `notes` を削除、`myPersonalNotes` / `myOtetsukiCount` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchCreateRequest.java` — `otetsukiCount` / `personalNotes` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchSimpleCreateRequest.java` — `notes` を `personalNotes` にリネーム、`otetsukiCount` を追加
- **依存タスク:** タスク2 (#172)
- **対応Issue:** #173

#### MatchDto変更

```java
// 削除: private String notes;
// 追加:
private String myPersonalNotes;    // リクエストユーザー自身のメモ
private Integer myOtetsukiCount;   // リクエストユーザー自身のお手付き回数
```

`fromEntity` メソッドはそのまま（個人メモはService層でセットする）。

#### MatchCreateRequest変更

```java
// 追加:
@Min(0) @Max(20)
private Integer otetsukiCount;     // nullable

private String personalNotes;      // nullable
```

#### MatchSimpleCreateRequest変更

```java
// 変更: notes → personalNotes
private String personalNotes;

// 追加:
@Min(0) @Max(20)
private Integer otetsukiCount;
```

---

### タスク4: バックエンド — Service / Controller 変更
- [x] 完了
- **概要:** 試合の作成/更新/取得時に `match_personal_notes` を操作するロジックを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — 作成/更新/取得メソッドに個人メモのupsert・結合ロジックを追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchController.java` — 認証ユーザーIDの取得・サービスへの受け渡し
- **依存タスク:** タスク3 (#173)
- **対応Issue:** #175

#### MatchService変更ポイント

**作成時（createMatchSimple / createMatch）:**
1. 試合を保存
2. `otetsukiCount` / `personalNotes` が両方nullでなければ、`MatchPersonalNote` をupsert
3. MatchDtoに個人メモ情報をセットして返却

**更新時（updateMatchSimple / updateMatch）:**
1. 試合を更新
2. `MatchPersonalNote` をupsert（findByMatchIdAndPlayerId → 存在すれば更新、なければ作成）

**取得時（各find系メソッドからDto変換する箇所）:**
1. リクエストユーザーのplayerIdで `MatchPersonalNote` を検索
2. `MatchDto` に `myPersonalNotes` / `myOtetsukiCount` をセット
3. 一覧取得時は `findByPlayerIdAndMatchIdIn` でまとめて取得（N+1回避）

#### MatchController変更ポイント

- 取得系エンドポイント: リクエストパラメータまたはヘッダーから現在のplayerIdを取得してServiceに渡す
- 既存の認証情報の取得方法を確認し、一貫した方法で `currentPlayerId` を取得する

---

### タスク5: フロントエンド — API・MatchForm変更
- [x] 完了
- **概要:** API関数のリクエスト/レスポンス対応を変更し、MatchFormにお手付き入力UIを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/matches.js` — 必要に応じてリクエストフィールド名を調整
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx` — formDataに `otetsukiCount` 追加、セレクトボックスUI追加、保存ロジック変更（`notes` → `personalNotes`）
- **依存タスク:** タスク4 (#175)
- **対応Issue:** #177

#### MatchForm変更ポイント

**formData:**
```javascript
const [formData, setFormData] = useState({
  matchDate: ...,
  opponentName: ...,
  opponentId: ...,
  result: '勝ち',
  scoreDifference: 0,
  matchNumber: ...,
  personalNotes: '',       // notes → personalNotes
  otetsukiCount: null,     // 新規追加
});
```

**お手付き入力UI（枚数差の下、メモの上に配置）:**
```jsx
<div>
  <div className="text-xs font-medium text-[#6b7280] tracking-wide mb-2">お手付き回数</div>
  <select
    name="otetsukiCount"
    value={formData.otetsukiCount ?? ''}
    onChange={(e) => setFormData(prev => ({
      ...prev,
      otetsukiCount: e.target.value === '' ? null : parseInt(e.target.value)
    }))}
    className="w-full px-0 py-3 border-0 border-b border-[#c5cec8] bg-transparent ..."
  >
    <option value="">未入力</option>
    {Array.from({ length: 21 }, (_, i) => i).map(num => (
      <option key={num} value={num}>{num} 回</option>
    ))}
  </select>
</div>
```

**保存ロジック:**
- `createDetailed` / `updateDetailed` 呼び出し時に `otetsukiCount` と `personalNotes` を含める
- `create` / `update`（簡易）呼び出し時も同様

**既存マッチ編集時:**
- APIレスポンスの `myOtetsukiCount` / `myPersonalNotes` をformDataに反映

---

### タスク6: フロントエンド — 表示画面の変更（MatchList / MatchResultsView / MatchDetail）
- [x] 完了
- **概要:** 自分の試合に対してお手付き回数とメモの有無を表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — 自分の試合行にメモアイコン・お手付き回数を表示
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — 自分の結果にお手付き回数・メモを表示
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — `match.notes` → `match.myPersonalNotes` に変更、お手付き回数表示を追加
- **依存タスク:** タスク4 (#175)
- **対応Issue:** #179

#### MatchList変更ポイント
- 自分の試合行（`currentPlayer.id` が player1 or player2 の場合）に:
  - メモありの場合: 小さなメモアイコン（lucide-react の `StickyNote` 等）を表示
  - お手付き回数がnullでない場合: 「お手付き: N回」を小さく表示

#### MatchResultsView変更ポイント
- 自分の試合結果表示部分に:
  - お手付き回数（nullでなければ表示）
  - メモ（あれば表示）

#### MatchDetail変更ポイント
- 既存の `match.notes` 表示を `match.myPersonalNotes` に変更
- お手付き回数の表示を追加（nullでなければ表示）

---

### タスク7: BulkResultInput のnotes参照を除去
- [x] 完了（BulkResultInputにnotes参照なし、変更不要）
- **概要:** BulkResultInputが `notes` フィールドを送信していないか確認し、もし送信していれば除去する。APIレスポンスの `notes` 参照も除去する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — notes関連の参照があれば除去
- **依存タスク:** タスク4 (#175)
- **対応Issue:** #181

---

## 実装順序

1. **タスク1**: DBスキーマ変更（依存なし）
2. **タスク2**: Entity / Repository 作成（タスク1に依存）
3. **タスク3**: DTO変更（タスク2に依存）
4. **タスク4**: Service / Controller 変更（タスク3に依存）
5. **タスク5**: フロントエンド — MatchForm変更（タスク4に依存）
6. **タスク6**: フロントエンド — 表示画面変更（タスク4に依存、タスク5と並行可能）
7. **タスク7**: BulkResultInput のnotes除去（タスク4に依存、タスク5・6と並行可能）
