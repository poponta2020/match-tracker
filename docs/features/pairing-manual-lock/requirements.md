---
status: completed
---
# 対戦組み合わせ手動ロック（pairing-manual-lock）要件定義書

## 1. 概要

### 目的
対戦組み合わせ（ペアリング）のうち、**結果がまだ入力されていない特定の組を、ユーザーが明示的にロック**できるようにする。ロックした組は自動組み合わせの再生成・一括保存・手動編集による変更から保護され、「この相手と試合したい」という希望を事前に確定させておける。

### 背景・動機
- 既存の「結果入力済みロック」（`match-result-lock`）は、対応する `matches` 結果レコードの存在で**暗黙的に**判定する仕組みであり、**結果がまだ無い組には適用できない**。
- 練習当日（または事前）に参加者が「その日の対戦を見て、この人と試合したい」と思った場合、**事前に自分の組だけを組んで確定（ロック）**しておきたいニーズがある。
- そのため、結果の有無とは独立した**明示的なロック状態**を `match_pairings` に持たせる。

### 既存実装との関係
- 保護の挙動（自動組み合わせ除外・一括保存で保持・ロック中は編集/削除不可）は既存の結果ロックと同等とし、サービス層のロック判定を「**結果あり（hasResult）OR 手動ロック（locked）**」に一般化して再利用する。

## 2. ユーザーストーリー

### 対象ユーザー
- **選手（PLAYER）**: 自分が出たい対戦を事前に組んでロックする主たる利用者。
- **管理者（ADMIN / SUPER_ADMIN）**: 組み合わせ全体を管理。ロック/解除も可能。

### 利用シナリオ
1. 練習当日（または事前）、選手Aが `/pairings` 画面でその日の対戦予定を見る。
2. 「選手Bと試合したい」と考え、A対Bの組を作成する。
3. その組にロック（鍵ボタン）をかける。
4. 後で誰か（管理者または別の選手）が自動組み合わせ・一括保存を実行しても、A対Bの組と両選手は保護され、残りの選手だけで組み合わせが行われる。
5. 不要になったら誰でもロックを解除でき、解除後は通常の組として再編集・再生成の対象に戻る。

### 確定した方針（ヒアリング結果）
| 論点 | 決定 |
|------|------|
| ロックできる人・対象 | **参加者なら誰でも、任意の組をロック可能**（所有者・対戦者本人かどうかは問わない） |
| 解除できる人 | **参加者なら誰でも解除可能**（ロックした本人に限定しない） |
| 相手の同意・通知 | **不要**（ロックする側が単独で実行。承認フロー・通知は実装しない） |
| ロックの効果 | **既存の結果ロックと同等**（①自動組み合わせから対象2名を除外 ②一括保存で削除されず保持 ③ロック中はドラッグ/編集/削除不可、解除して初めて変更可能） |
| ロック操作 | **組ごとの鍵ボタンで即ロック/解除**（専用API・即時反映。未保存の組は自動保存してからロック） |
| 二重ブッキング防止 | **ロック時にサーバーで「1選手1組」を担保**（競合時はエラー） |
| 解除後の挙動 | **組は残り、通常の未ロック組に戻る** |
| 粒度 | **回戦（match_number）単位** |
| ロック状態のDB保持 | **`locked` boolean フラグのみ** |
| ロック表示の範囲 | **編集画面（PairingGenerator）のみ**（PairingSummary は現状維持） |

## 3. 機能要件

### 3.1 画面仕様（PairingGenerator / `/pairings`）

**ロックボタン**
- 未ロックかつ編集可能な各組に「ロック（鍵アイコン）」ボタンを表示する。
- 押下で即時ロック。対象の組が未保存（DB未登録）の場合は、まず現在の編集状態を保存（`createBatch`）してから、付与された `id` に対してロックAPIを呼ぶ。

**手動ロック済み組の表示**
- 専用バッジ（例: 🔒「ロック」）を表示する。
- グレーアウトし、ドラッグ・選手変更・削除を不可にする（結果ロックと同様）。
- 「解除」ボタンを全ロール（PLAYER/ADMIN/SUPER_ADMIN）に表示する。押下で即時解除し、ペアリングを再取得して表示を更新する。

**結果入力済みロック（既存）との区別**
- 結果ロック: 「結果入力済」バッジ＋ADMIN以上のみ「リセット」（組＋結果を削除）。— 現状維持。
- 手動ロック: 別バッジ＋全員が「解除」（組は残しフラグのみ解除）。
- 両者を視覚的・操作的に区別する。

**自動組み合わせ・一括保存・回戦削除**
- 手動ロック組と両選手を保護対象に加える（結果ロックと同様に、自動組み合わせの対象から除外し、保存・回戦削除でも保持）。

**表示範囲**
- ロック表示・操作は PairingGenerator（編集画面）のみ。PairingSummary（閲覧専用）は現状維持。

### 3.2 ビジネスルール
- **ロック/解除権限**: 参加者なら誰でも（PLAYER/ADMIN/SUPER_ADMIN）。所有者・対戦者チェックなし。組織スコープは既存どおり（`validateScopeByPairingId`）。
- **保護判定（ロック扱い）** = `hasResult`（結果あり）**OR** `locked`（手動ロック）。
- **二重ブッキング防止**: ロック時、同一 `(session_date, match_number)`・同一組織スコープ内で、対象組の2選手いずれかを含む**他の組**が存在すればロックを拒否し、エラーを返す。通常の組作成・一括保存は既存挙動のまま（編集UIで自然に防止されるため変更しない）。
- **解除**: `locked` を false にするだけ。組は残り、通常の未ロック組（編集・削除・自動再生成の対象）に戻る。
- **結果ロックとの併存**: 手動ロック中の組に後から結果が入った場合、`hasResult=true` となり結果ロックとしても保護される。手動ロックを解除しても、結果が存在する限り結果ロックとして保護は継続する。
- **粒度**: `(session_date, match_number)` 単位。回戦ごとに個別ロック。1人が複数組（複数回戦）をロックすることも可（上限なし）。

### 3.3 エラーケース
- 二重ブッキング（ロック対象選手が同回戦の別組に存在）→ エラー＋メッセージ（例: 「選手〇〇は既に別の組に入っています」）。
- 存在しない組のロック/解除 → 404。
- 組織スコープ外の操作 → 403（既存 `validateScopeByPairingId`）。

## 4. 技術設計

### 4.1 DB設計
- `match_pairings` に `locked` カラムを追加する。

```sql
-- database/add_locked_to_match_pairings.sql（新規）
ALTER TABLE match_pairings ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
```

- **⚠️ 本番DB（Render PostgreSQL）への適用が必須**（CLAUDE.md「DBマイグレーション適用ルール」）。entity に `locked` を追加するため、未適用だと起動時/クエリでカラム不一致エラーになる。entity 変更とマイグレーションSQLを同一PRに含め、PRで本番適用が必要な旨を明示する。

### 4.2 API設計

**新規エンドポイント**（id ベース。`validateScopeByPairingId` を適用。`resetWithResult` と同じパターン）

| メソッド | パス | 説明 | 権限 |
|----------|------|------|------|
| PATCH | `/api/match-pairings/{id}/lock` | 指定組をロック（二重ブッキング検証付き） | PLAYER / ADMIN / SUPER_ADMIN |
| PATCH | `/api/match-pairings/{id}/unlock` | 指定組のロックを解除 | PLAYER / ADMIN / SUPER_ADMIN |

- 戻り値は更新後の `MatchPairingDto`。
- lock 時、二重ブッキング検証に違反した場合はエラー（例: 409 Conflict）を返す。

**変更エンドポイント**

| メソッド | パス | 変更内容 |
|----------|------|----------|
| POST | `/api/match-pairings/batch` | 保護判定を `hasResult OR locked` に一般化（手動ロック組を保持、両選手を新規ペアから除外） |
| POST | `/api/match-pairings/auto-match` | 同上（手動ロック選手を除外し、`lockedPairings` に手動ロック組も含めて返す） |
| DELETE | `/api/match-pairings/date-and-match` | 保護判定を一般化（手動ロック組は削除しない） |
| GET | `/api/match-pairings/date`, `/date-and-match` | レスポンス `MatchPairingDto` に `locked` を含める |

### 4.3 Entity / DTO
- `MatchPairing.java`: `@Column(name = "locked", nullable = false) @Builder.Default private Boolean locked = false;`
  - createBatch / autoMatch の `MatchPairing.builder()...` は `locked` を明示設定しないため、`@Builder.Default` で false を補完する（NULL 回避）。
- `MatchPairingDto.java`: `private boolean locked;` を追加。`convertToDto()` / `convertToDtoWithCache()` で entity の `locked` をマップする。

### 4.4 バックエンド設計
- `MatchPairingService`:
  - 新メソッド `lock(Long id)` / `unlock(Long id)`（または `setLocked(id, boolean)`）。`lock` 時に二重ブッキング検証（`findBySessionDateAndMatchNumber` で同回戦の他組を取得し、対象2選手の重複をチェック）。
  - `createBatch()` / `autoMatch()` / `deleteByDateAndMatchNumber()` のロック判定を「`hasResult OR locked`」に一般化する。共通ヘルパ（例: `isLocked(pairing, matches)`）を導入して重複を削減する。
- `MatchPairingController`: lock/unlock エンドポイント追加（`resetWithResult` と同じ id ベースのスコープ検証・organizationId 解決パターン）。
- `MatchPairingRepository`: 二重ブッキング検証は既存 `findBySessionDateAndMatchNumber` を利用（追加不要の見込み）。

### 4.5 フロントエンド設計
- `pairings.js`: `lock(id)` / `unlock(id)`（PATCH）を追加。
- `PairingGenerator.jsx`:
  - pairing オブジェクトに `locked` を反映。
  - ロックボタン（未ロック・編集可能組）／解除ボタン（手動ロック組）の追加とハンドラ実装（lock: 必要なら保存→lock、unlock: unlock→再取得）。
  - 保護判定を `p.hasResult` から `(p.hasResult || p.locked)` に一般化（`handleSave` のフィルタ [357,368-369行]、ドラッグ可否、表示分岐）。
  - `handleAutoMatch`: `lockedPairings` を `hasResult: true` 固定でマップせず（現 325-328行）、DTO の `hasResult` / `locked` をそのまま尊重する。
  - 手動ロックと結果ロックでバッジ・操作ボタンを出し分ける。

## 5. 影響範囲

### 変更が必要な既存ファイル
**バックエンド**
| ファイル | 変更内容 |
|----------|----------|
| `MatchPairing.java` | `locked` カラム追加（@Builder.Default false） |
| `MatchPairingDto.java` | `locked` フィールド追加・マッピング |
| `MatchPairingController.java` | lock / unlock エンドポイント追加 |
| `MatchPairingService.java` | lock / unlock メソッド、ロック判定の一般化、二重ブッキング検証 |

**フロントエンド**
| ファイル | 変更内容 |
|----------|----------|
| `pairings.js` | lock / unlock API 追加 |
| `PairingGenerator.jsx` | ロック表示・操作ボタン・保護判定の一般化・autoMatch マッピング修正 |

**DB**
- `database/add_locked_to_match_pairings.sql`（新規）＋ **本番DB適用**

**ドキュメント**
- `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md` を更新

### 既存機能への影響
- **結果入力済みロック**: 判定式を一般化するが、`locked` のデフォルト false により既存挙動は不変。
- **自動組み合わせ / 一括保存 / 回戦削除**: 手動ロックも保護対象に加わる（動作拡張）。`locked=false` の既存データには影響なし。
- **PairingSummary・他画面**: 影響なし（ロック表示は編集画面のみ）。
- **DTO 追加フィールド**: 後方互換（フロントは未知フィールドを無視可）。

### リスク・注意点
- **DBマイグレーションの本番適用忘れ**（CLAUDE.md 最重要ルール）。entity に NOT NULL カラムを追加するため、未適用だとカラム不一致エラーのリスク。
- `handleAutoMatch` の `hasResult: true` 固定を外す変更は、結果ロック表示の回帰に注意（テストで担保）。
- `@Builder` 使用エンティティの `locked` デフォルト（NULL 回避）。

## 6. 設計判断の根拠
| 判断 | 理由 |
|------|------|
| `locked` boolean のみ（`locked_by` 等を持たない） | 誰でもロック/解除可・所有者チェック不要のため、機能上 boolean で十分。最小スキーマ変更。 |
| ロック判定を「結果 OR locked」に一般化 | 既存の結果ロックの保護ロジック（除外・保持・編集不可）をそのまま再利用でき、二重実装を避けられる。 |
| 専用 PATCH lock/unlock（即時反映） | 結果ロックのリセットボタンと同じ「組ごとの即時操作」UX。一括保存と切り離し、未保存組は自動保存してからロックする運用が明快。 |
| 二重ブッキング検証はロック時のみ | ロックは「確定」操作であり整合性担保が最重要。通常の作成・保存は編集UIで自然に防止されており、既存挙動を変えず影響範囲を最小化。 |
| 解除はフラグを落とすだけ（組は残す） | 結果のない組には消すべき結果がなく、未ロック組として再利用するのが自然。結果ロックのリセット（組＋結果削除）とは目的が異なる。 |
| 全ロールが解除可 | ヒアリング結果（誰でも解除可）。小規模クラブの協調的運用に合致。 |
| ロック表示は編集画面のみ | ヒアリング結果。閲覧サマリは現状維持で影響最小。 |
