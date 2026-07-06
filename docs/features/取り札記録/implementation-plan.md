---
status: draft
---
# 取り札記録 実装手順書

要件: [requirements.md](./requirements.md)（completed）／設計: [design-spec.md](./design-spec.md)（locked）／決まり字: [kimariji-master.md](./kimariji-master.md)。

## 実装タスク

### タスク1: DBマイグレーションSQL（3テーブル）
- [ ] 完了
- **概要:** `card_rule_nonce` / `match_card_placements` / `match_otetsuki_details` を追加。**本番PostgreSQLにも適用（CLAUDE.md最重要ルール）。**
- **変更対象:** `database/<新規>.sql`（既存命名規則に合わせる）
- **依存:** なし

### タスク2: バックエンド Entity / Repository（3種）
- [ ] 完了
- **概要:** `CardRuleNonce`, `MatchCardPlacement`, `MatchOtetsukiDetail` エンティティ＋各リポジトリ。JstDateTimeUtil で createdAt/updatedAt。
- **変更対象:** `entity/*.java`, `repository/*.java`
- **依存:** タスク1（スキーマ整合）

### タスク3: CardRuleNonce API（GET/PUT）
- [ ] 完了
- **概要:** `GET /api/card-rule-nonce?date=YYYY-MM-DD` → `{date,nonce}`（未登録は0）、`PUT /api/card-rule-nonce {date,nonce}`。Service/Controller/DTO。権限は既存ペアリング操作と同等（要 @RequireRole 確認）。
- **変更対象:** `controller/CardRuleNonceController.java`, `service/CardRuleNonceService.java`, `dto/CardRuleNonceDto.java`
- **依存:** タスク2

### タスク4: Match 詳細API拡張（配置・お手付き詳細の upsert / 取得）
- [ ] 完了
- **概要:** 詳細版 create/update リクエストに `cardPlacements[]` / `otetsukiDetails[]` を追加し、認証ユーザー(player_id)分を全置換upsert。Match取得系レスポンスに `myCardPlacements` / `myOtetsukiDetails` を追加（本人分のみ）。簡易フローも可能なら対応。
- **変更対象:** `controller/MatchController.java`, `service/MatchService.java`, Match系DTO・リクエストDTO, `dto/CardPlacementDto.java`, `dto/OtetsukiDetailDto.java`
- **依存:** タスク2
- **認可:** 本人のみ読み書き（player_id == 認証ユーザー）

### タスク5: フロント 決まり字マスター定数 + 札ルール→50枚展開
- [ ] 完了
- **概要:** `src/data/kimariji.js`（cardNo→決まり字, kimariji-master準拠）。`pairings/cardRules.js` に「ルール→札番号配列(50枚)」展開の純関数を追加（ones/tens/nuki）。
- **変更対象:** `karuta-tracker-ui/src/data/kimariji.js`（新規）, `pairings/cardRules.js`
- **依存:** なし

### タスク6: フロント nonce の DB 化
- [ ] 完了
- **概要:** `api/cardRuleNonce.js`（新規クライアント）。`pairings/cardRules.js` の loadNonce/saveNonce を DB優先に。`PairingSummary.jsx` の「札を再生成」を DB nonce 更新に。同日内固定の要件は維持。
- **変更対象:** `api/cardRuleNonce.js`, `api/index.js`, `pairings/cardRules.js`, `pairings/PairingSummary.jsx`
- **依存:** タスク3
- **影響:** 既存 pairing-card-rule-persistence の挙動（localStorage→DB）。回帰確認。

### タスク7: フロント 取り札盤面 + お手付き詳細 + MatchForm統合
- [ ] 完了
- **概要:** design-spec のA案（畳・縦札・左右分割・折りたたみ）を実装。出札50枚導出→盤面/不明プール、24マス配置、お手付き回数連動フォーム（種類別）。MatchForm 保存時に `cardPlacements`/`otetsukiDetails` 送信、編集時 `my*` 復元。CSSは design-spec の `_torifuda-kiroku.css` を移植（`.tk`スコープ→本番CSSへ）。
- **変更対象:** `pages/matches/TorifudaBoard.jsx`（新規）, `OtetsukiDetails.jsx`（新規）, `MatchForm.jsx`, `MatchForm.css`/新規CSS, `api/matches` クライアント
- **依存:** タスク4・5・6

### タスク8: テスト
- [ ] 完了
- **概要:** backend: CardRuleNonce・配置/お手付きupsert・本人限定取得のサービス/コントローラテスト。frontend: ルール→50枚展開・決まり字マスター整合（100枚一意）・盤面配置ロジックの単体。
- **依存:** タスク3-7

### タスク9: ビルド/検証 → 本番DB適用 → ship
- [ ] 完了
- **概要:** `./gradlew build`・`npm run build`/`lint`。本番PostgreSQLへマイグレーション適用（CLAUDE.local.md接続情報、JDBC+IPv4）。commit/push/PR/merge/close。
- **依存:** 全タスク

## 実装順序
1. タスク1（SQL）→ 2（Entity/Repo）
2. タスク3（nonce API）・タスク4（Match拡張API）
3. タスク5（決まり字/展開）・タスク6（nonce DB化）
4. タスク7（UI統合）
5. タスク8（テスト）→ タスク9（ビルド/本番適用/ship）
