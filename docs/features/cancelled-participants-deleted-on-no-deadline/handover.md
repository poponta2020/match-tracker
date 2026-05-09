# 引き継ぎ書: 練習詳細モーダルでキャンセル者が表示されない不具合

最終更新: 2026-04-30 12:50 JST
作成者: Claude (前セッション)
GitHub Issue: [#616](https://github.com/poponta2020/match-tracker/issues/616)

---

## 1. 背景・ユーザ報告

ユーザの報告:
> /practice のカレンダーから遷移する参加者を確認する画面で、キャンセル者が表示されなくなりました。
> 4/30 の北海道大学かるた会の練習です。
> 締め切りをなし、の設定にした後な気がします。

該当画面: `PracticeList.jsx` のカレンダー日付セルをクリックして開く詳細モーダル。試合別アコーディオンを展開すると「キャンセル済み」セクションが従来表示されていたが、表示されなくなった。

---

## 2. 調査で判明した事実

### 2-1. DB の現状（本番 Render PostgreSQL）

- 4/30 北大練習の `practice_session.id = 938`
- `practice_participants` のステータス分布（4月の北大全セッション）:
  - `WON`: 539 件
  - `PENDING`: 3 件
  - `CANCELLED` / `DECLINED` / `WAITLIST_DECLINED`: **0 件**
- すなわち、キャンセル履歴レコードが DB から消えている。

### 2-2. システム設定の変更履歴

`system_settings` テーブル:

| key | value | org_id | updated_at | updated_by |
|---|---|---|---|---|
| `lottery_deadline_days_before` | `-1` | 2（北大） | **2026-04-30 12:38:23** | 21 |
| `lottery_normal_reserve_percent` | 30 | 2 | 2026-04-30 12:18:27 | 21 |

→ 本日 12:38 に北大の締切設定を「-1（締切なし）」に変更している。ユーザの体感と一致。

### 2-3. 想定される根本原因

`DensukeImportService.processPhase1()`（`karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`、208〜216 行目あたり）:

```java
// not-○: 既存レコード(dirty=false)を削除（1-E）
for (PracticeParticipant p : existing) {
    if (!markedPlayerIds.contains(p.getPlayerId()) && !p.isDirty()) {
        practiceParticipantRepository.delete(p);   // ← 物理削除
    }
}
```

データの流れ:

1. 抽選確定後、ユーザがアプリで参加キャンセル
   → `status = CANCELLED, dirty = true` で保存
2. `DensukeWriteService` が伝助に「×」を書き戻し（同期成功）
   → `dirty = false` に更新
3. 次の伝助インポート時、Phase 1 を実行
4. その CANCELLED レコードは「○ マークされていない（×）」かつ `dirty = false` なので、上記の物理削除条件にヒット
5. **キャンセル履歴ごとレコードが物理削除される**

### 2-4. なぜ「締切なし」を設定すると顕在化したか

`DensukeImportService.determinePhase()` の分岐:

```java
if (lotteryDeadlineHelper.isBeforeDeadline(year, month, organizationId)) {
    return ImportPhase.PHASE1;
}
if (lotteryService.isLotteryConfirmed(year, month, organizationId)) {
    return ImportPhase.PHASE3;
}
return ImportPhase.PHASE2;
```

- 締切なしモード（`-1`）では `isBeforeDeadline` が常に `true`
- → 抽選確定後であっても、伝助インポートは常に Phase 1 で実行される
- → 抽選確定後に作られた CANCELLED レコードまで物理削除の対象になる

設定変更前は Phase 3（物理削除なし）で動いていたため、問題が顕在化していなかった。

### 2-5. 既に消えたデータの復旧可否

- 物理削除なので DB からは復旧不可
- 伝助側も「×」表記が残るのみで、いつ・誰がキャンセルしたかの履歴は残らない
- アプリのログ（`log.info("Phase1: removed non-○ participant ...")`）から痕跡を辿れる可能性あり（要確認）

---

## 3. 修正方針案（未決定）

### 案 A: Phase 1 の削除条件に terminal status 除外を追加（軽微・局所的）

```java
for (PracticeParticipant p : existing) {
    if (!markedPlayerIds.contains(p.getPlayerId())
        && !p.isDirty()
        && !isTerminalStatus(p.getStatus())) {   // ← 追加
        practiceParticipantRepository.delete(p);
    }
}
```

- `isTerminalStatus` は CANCELLED / DECLINED / WAITLIST_DECLINED を判定する既存ヘルパー。
- メリット: 影響範囲が小さい。
- デメリット: 「締切前 = Phase 1」というそもそもの分岐がそのまま残るため、締切なしモードの意味付けが曖昧。

### 案 B: 締切なし設定のときは Phase 3 を使うよう determinePhase を変更（やや大きめ）

「締切なし＝抽選を運用しない＝Phase 3 と同等」という解釈に基づく修正。

- メリット: 意味的にきれい。Phase 3 はキャンセル履歴を保持する設計なので、別の関連バグも未然に防げる可能性あり。
- デメリット: Phase 3 は「抽選確定後」を前提に書かれているため、`isLotteryConfirmed = false` のまま Phase 3 を流す副作用の精査が必要。WaitlistPromotion 等の挙動への影響を要検証。

### 案 A + B の併用も可

Phase 1 の安全性向上（A）＋ 意味的整理（B）を同時に行うアプローチ。

---

## 4. 中断時点の状況

- ✅ Issue 作成済み: [#616](https://github.com/poponta2020/match-tracker/issues/616)
- ✅ 調査完了（DB 確認・コードリーディング・原因仮説確定）
- ⏸️ 修正方針について**ユーザ確認待ち**（案 A / B / その他）
- ❌ Worktree 未作成
- ❌ コード未修正
- ❌ PR 未作成

ブランチ: `main`（クリーン、関連変更なし）

---

## 5. 再開時の次のアクション

### Step 1: ユーザに方針確認

以下を聞いてから着手:

1. **修正方針** — 案 A / B / 併用 / その他、どれで進めるか
2. **「締切なし」設定の意図** — 抽選運用をやめる意図なら案 B 寄り。一時的に締切を緩めたいだけなら案 A で十分。
3. **既に消えたキャンセル履歴の扱い** — 手動で再投入するか、見えないままで良いか
4. **緊急対応の必要性** — 同様のデータロスを止めるため、修正前に一時的に北大の `lottery_deadline_days_before` を元の値に戻す運用対応を取るか

### Step 2: Worktree 作成

```bash
git worktree add /tmp/fix-cancelled-display-on-no-deadline -b fix/cancelled-display-on-no-deadline origin/main
```

### Step 3: 実装（方針確定後）

- 対象ファイル: `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`
- 案 A の場合: `processPhase1` の削除ループに条件追加
- 案 B の場合: `determinePhase` の分岐に noDeadline 判定を追加
- 既存テスト: `DensukeImportServiceTest`, `DensukeImportServicePhaseCoverageTest` への影響を確認、必要に応じてテスト追加

### Step 4: PR 作成・レビュー依頼

通常フローに沿う（`Fixes #616` を含める）。

---

## 6. 関連ファイル・参照先

### コード

- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`
  - `determinePhase()` 165-181 行目
  - `processPhase1()` 187-281 行目（特に 208-216 の物理削除ループ）
  - `reactivatePhase1()` 643-661 行目
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java`
  - `isNoDeadline()` 35-37 行目
  - `isBeforeDeadline()` 76-81 行目
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java`
  - 同期成功で `setDirty(false)` する箇所: 491-498 行目
- `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
  - キャンセル済み表示部: 897-918 行目あたり
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
  - `enrichDtoWithMatchDetails()` 707-766 行目（CANCELLED は表示対象に含まれているので問題はバックエンド表示側ではない）

### DB 接続

本番 Render PostgreSQL を使用。接続情報（ホスト・ユーザー・パスワード・DB 名）は `CLAUDE.local.md`（gitignore 対象）を参照すること。

### Issue

[#616 練習詳細モーダルでキャンセル者が表示されない（締切なし設定後の伝助同期で物理削除）](https://github.com/poponta2020/match-tracker/issues/616)
