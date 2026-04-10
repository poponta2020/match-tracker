---
status: completed
---
# メンターフィードバック LINE通知バッチ送信 改修実装手順書

## 実装タスク

### タスク1: Entity・DTO・Repository の変更（データモデル層）
- [x] 完了
- **概要:** `match_comments` テーブルに `line_notified` カラムを追加し、Entity・DTO・Repositoryに反映する。後続タスクの基盤となる変更。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchComment.java` — `lineNotified` フィールド追加（`@Builder.Default private Boolean lineNotified = false`）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchCommentDto.java` — `lineNotified` フィールド追加、`fromEntity()` に反映
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchCommentRepository.java` — 未通知コメント検索クエリ追加
  - `database/` — DDLマイグレーションスクリプト追加
- **依存タスク:** なし
- **対応Issue:** #416

#### 実装詳細

**MatchComment.java に追加:**
```java
@Builder.Default
@Column(name = "line_notified", nullable = false)
private Boolean lineNotified = false;
```

**MatchCommentDto.java に追加:**
```java
private Boolean lineNotified;
```
`fromEntity()` に `.lineNotified(entity.getLineNotified())` を追加。

**MatchCommentRepository.java に追加:**
```java
@Query("SELECT c FROM MatchComment c WHERE c.matchId = :matchId AND c.menteeId = :menteeId AND c.authorId = :authorId AND c.lineNotified = false AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
List<MatchComment> findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(
    @Param("matchId") Long matchId, @Param("menteeId") Long menteeId, @Param("authorId") Long authorId);
```

**DDLマイグレーション:**
```sql
ALTER TABLE match_comments ADD COLUMN line_notified BOOLEAN NOT NULL DEFAULT false;
UPDATE match_comments SET line_notified = true;
```

---

### タスク2: Service変更（即時通知廃止 + バッチ通知ロジック）
- [x] 完了
- **概要:** `createComment()` から即時LINE通知を削除し、新たにバッチ通知送信メソッドを追加する。Flex Message構築ロジックも実装する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchCommentService.java` — 即時通知削除、`sendCommentNotification()` メソッド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendMentorCommentFlexNotification()` メソッド追加
- **依存タスク:** タスク1（#416）
- **対応Issue:** #417

#### 実装詳細

**MatchCommentService.java の変更:**

1. `createComment()` 60行目の `lineNotificationService.sendMentorCommentNotification(...)` 呼び出しを削除

2. 新規メソッド `sendCommentNotification()` を追加:
```java
@Transactional
public Map<String, Object> sendCommentNotification(Long matchId, Long menteeId, Long currentUserId) {
    validateCommentAccess(menteeId, currentUserId);
    validateMatchBelongsToMentee(matchId, menteeId);

    // 自分が書いた未通知コメントを取得
    List<MatchComment> unnotified = matchCommentRepository
        .findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(matchId, menteeId, currentUserId);

    if (unnotified.isEmpty()) {
        throw new IllegalStateException("未通知のコメントがありません");
    }

    // 試合情報取得
    Match match = matchRepository.findById(matchId)
        .orElseThrow(() -> new ResourceNotFoundException("Match", matchId));

    // Flex Message送信
    LineNotificationService.SendResult result;
    if (currentUserId.equals(menteeId)) {
        // メンティーがコメント → 全ACTIVEメンターに通知
        result = lineNotificationService.sendMentorCommentFlexNotification(
            currentUserId, menteeId, match, unnotified, true);
    } else {
        // メンターがコメント → メンティーに通知
        result = lineNotificationService.sendMentorCommentFlexNotification(
            currentUserId, menteeId, match, unnotified, false);
    }

    // 通知済みフラグを更新
    for (MatchComment comment : unnotified) {
        comment.setLineNotified(true);
    }
    matchCommentRepository.saveAll(unnotified);

    return Map.of(
        "notifiedCount", unnotified.size(),
        "result", result.name()
    );
}
```

**LineNotificationService.java に追加:**

`sendMentorCommentFlexNotification()` メソッド:
- 引数: authorId, menteeId, match, comments, isMenteeAuthor
- 処理:
  1. 送信者名を取得
  2. 試合の対戦相手名を解決（player1/player2からmentee以外を特定）
  3. Flex Message を構築（下記参照）
  4. `isMenteeAuthor = true` の場合: 全ACTIVEメンターに `sendFlexToPlayer()` で送信
  5. `isMenteeAuthor = false` の場合: メンティーに `sendFlexToPlayer()` で送信
  6. 送信結果を返却

**Flex Message設計:**
```
┌─────────────────────────────────┐
│ フィードバックコメント            │  ← Header（背景色 #4a6b5a）
├─────────────────────────────────┤
│ ○○さんからのフィードバック        │  ← 送信者名（bold）
│ 4/10 第1試合 vs ○○              │  ← 試合情報（gray）
│ ─────────────────────────────── │  ← separator
│ コメント1の内容                   │  ← コメント（wrap: true）
│ ─────────────────────────────── │  ← separator
│ コメント2の内容                   │  ← コメント（wrap: true）
│ ─────────────────────────────── │  ← separator
│ コメント3の内容                   │  ← コメント（wrap: true）
└─────────────────────────────────┘
```

Header:
```java
Map.of(
    "type", "box", "layout", "vertical",
    "contents", List.of(
        Map.of("type", "text", "text", "フィードバックコメント",
            "color", "#ffffff", "weight", "bold", "size", "md")
    ),
    "backgroundColor", "#4a6b5a", "paddingAll", "15px"
)
```

Body: 送信者名 + 試合情報 + separator + コメント一覧（各コメントを separator で区切り）

altText: `○○さんがフィードバックコメントをN件投稿しました`

---

### タスク3: Controller変更（通知送信APIエンドポイント追加）
- [x] 完了
- **概要:** 通知送信用のAPIエンドポイントを `MatchCommentController` に追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchCommentController.java` — `POST /notify` エンドポイント追加
- **依存タスク:** タスク2（#417）
- **対応Issue:** #418

#### 実装詳細

```java
@PostMapping("/notify")
public ResponseEntity<Map<String, Object>> sendNotification(
        @PathVariable Long matchId,
        @RequestParam Long menteeId,
        HttpServletRequest httpRequest) {
    Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
    log.info("コメントLINE通知送信: matchId={}, menteeId={}, by={}", matchId, menteeId, currentUserId);
    Map<String, Object> result = matchCommentService.sendCommentNotification(matchId, menteeId, currentUserId);
    return ResponseEntity.ok(result);
}
```

---

### タスク4: Frontend変更（LINE通知送信ボタン追加）
- [x] 完了
- **概要:** コメントスレッド画面に「LINE通知を送信」ボタンを追加し、未通知コメントの有無に応じて表示制御する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/matchComments.js` — `sendNotification()` API関数追加
  - `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx` — 送信ボタンUI追加、未通知コメント計算ロジック追加
- **依存タスク:** タスク3（#418）
- **対応Issue:** #419

#### 実装詳細

**matchComments.js に追加:**
```javascript
sendNotification: (matchId, menteeId) =>
  apiClient.post(`/matches/${matchId}/comments/notify`, null, { params: { menteeId } }),
```

**MatchCommentThread.jsx の変更:**

1. 未通知コメント件数の算出:
```javascript
const unnotifiedCount = comments.filter(
  c => c.authorId === currentPlayer?.id && !c.lineNotified
).length;
```

2. 通知送信ハンドラ追加:
```javascript
const [notifying, setNotifying] = useState(false);
const [notifySuccess, setNotifySuccess] = useState(false);

const handleSendNotification = async () => {
  try {
    setNotifying(true);
    await matchCommentsAPI.sendNotification(matchId, menteeId);
    setNotifySuccess(true);
    await fetchComments(); // lineNotified フラグを更新
    setTimeout(() => setNotifySuccess(false), 3000);
  } catch (err) {
    setError(err.response?.data?.message || 'LINE通知の送信に失敗しました');
  } finally {
    setNotifying(false);
  }
};
```

3. ボタンUI（投稿フォームの上に配置）:
```jsx
{unnotifiedCount > 0 && (
  <div className="flex justify-end mb-2">
    <button
      onClick={handleSendNotification}
      disabled={notifying}
      className="flex items-center gap-1 bg-[#06C755] text-white px-3 py-1.5 rounded-full text-sm hover:bg-[#05b54c] disabled:opacity-50"
    >
      <Bell size={14} />
      {notifying ? '送信中...' : `LINE通知を送信（${unnotifiedCount}件）`}
    </button>
  </div>
)}
{notifySuccess && (
  <div className="text-sm text-green-600 text-right mb-2">LINE通知を送信しました</div>
)}
```
- ボタン色: `#06C755`（LINE公式グリーン）
- `lucide-react` の `Bell` アイコンを使用

## 実装順序
1. タスク1（依存なし）— データモデル層の変更
2. タスク2（タスク1に依存）— バックエンドロジック
3. タスク3（タスク2に依存）— APIエンドポイント
4. タスク4（タスク3に依存）— フロントエンドUI
