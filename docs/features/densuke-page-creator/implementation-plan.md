---
status: completed
---
# densuke-page-creator 実装手順書

参照: [requirements.md](./requirements.md)
親Issue: [#452](https://github.com/poponta2020/match-tracker/issues/452)

## 実装タスク

### タスク1: densuke.biz 新規ページ作成フォームの解析（前提調査）
- [x] 完了（2026-04-17、結果は densuke-form-spec.md）
- **概要:** 実装に入る前に densuke.biz 側の仕様を実測する。ユーザー（管理者）が伝助で実際に新規ページを作成する操作を行い、ブラウザの DevTools で通信ログを取得してもらう。得た情報で `requirements.md` の「4.5 未確定事項」を埋める
- **変更対象ファイル:**
  - `docs/features/densuke-page-creator/densuke-form-spec.md`(新規) — 解析結果を記録
  - `docs/features/densuke-page-creator/requirements.md` — 4.5 節を更新
- **確認項目:**
  - 新規ページ作成エンドポイント（URL パス、HTTP メソッド）
  - 作成フォームの必須／任意フィールド名と送信値の仕様
  - ログイン認証の要否（ユーザー認識では不要）
  - 作成完了レスポンスから `cd` を取得する方法（Location ヘッダー／本文パース）
  - 日程行（日付×試合番号×時刻×会場ラベル）の書き込みエンドポイントとフォーム構造
  - `contact_email`（連絡先メアド）が伝助のどのフィールドに入るか
- **依存タスク:** なし
- **対応 Issue:** [#453](https://github.com/poponta2020/match-tracker/issues/453)

### タスク2: densuke_templates テーブル作成
- [x] 完了（2026-04-17）
- **追加対応:** densuke_urls に sd カラム追加（タスク1で判明）
- **概要:** テンプレート保存用の新規テーブル・Entity・Repository を作成
- **変更対象ファイル:**
  - `database/create_densuke_templates.sql`(新規) — マイグレーション SQL
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/DensukeTemplate.java`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/DensukeTemplateRepository.java`(新規)
- **依存タスク:** なし
- **対応 Issue:** [#454](https://github.com/poponta2020/match-tracker/issues/454)

### タスク3: line_notification_preferences への列追加
- [x] 完了（2026-04-17）
- **概要:** LINE 通知設定テーブルに `densuke_page_created` 列を追加し、Entity と DTO を更新
- **変更対象ファイル:**
  - `database/add_densuke_page_created_line_preference.sql`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineNotificationPreferenceDto.java`(または該当 DTO) — 入出力対応
- **依存タスク:** なし
- **対応 Issue:** [#455](https://github.com/poponta2020/match-tracker/issues/455)

### タスク4: LineNotificationType enum に DENSUKE_PAGE_CREATED 追加
- [x] 完了（2026-04-17）
- **追加対応:** `LineNotificationService.isLineTypeEnabled()` の switch 式に case 追加
- **概要:** 通知種別 enum に新規種別を追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationType.java`（または該当 enum 定義ファイル）
- **依存タスク:** なし
- **対応 Issue:** [#456](https://github.com/poponta2020/match-tracker/issues/456)

### タスク5: DensukeTemplateService と DTO 実装
- [x] 完了（2026-04-17）
- **概要:** テンプレート CRUD のサービス層、入出力 DTO を実装
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeTemplateService.java`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/DensukeTemplateDto.java`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/DensukeTemplateUpdateRequest.java`(新規)
- **主要メソッド:**
  - `getTemplate(orgId)` — テンプレート取得。未登録団体にはデフォルト値返却
  - `updateTemplate(orgId, request)` — テンプレート更新
- **依存タスク:** タスク2
- **対応 Issue:** [#457](https://github.com/poponta2020/match-tracker/issues/457)

### タスク6: LineNotificationService への通知メソッド追加
- [x] 完了（2026-04-17）
- **概要:** 伝助ページ作成通知を LINE で送信するメソッドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendDensukePageCreatedNotification(organizationId, year, month, densukeUrl)` 追加
- **処理:**
  - 指定団体の PLAYER ロールメンバーを列挙
  - メッセージ組み立て（`{month}月の練習日程が出ました` + 本文）
  - `sendToPlayer(playerId, DENSUKE_PAGE_CREATED, message)` を個別に呼ぶ
  - 送信失敗はログ記録のみで継続
- **依存タスク:** タスク3, タスク4
- **対応 Issue:** [#458](https://github.com/poponta2020/match-tracker/issues/458)

### タスク7: DensukePageCreateService 実装（コア）
- [x] 完了（2026-04-17）
- **概要:** 伝助ページ作成の中核ロジック
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukePageCreateService.java`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/DensukePageCreateRequest.java`(新規)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/DensukePageCreateResponse.java`(新規)
- **処理フロー:**
  1. バリデーション（practice_sessions 0件、venue_match_schedules 不整合、densuke_urls 既存）
  2. テンプレート解決（プレースホルダー置換、overrides 適用）
  3. densuke.biz に新規ページ作成 POST（タスク1 の調査結果に基づく）
  4. 日程行書き込み（日付×試合×時刻×会場）
  5. densuke_urls にレコード保存
  6. `@TransactionalEventListener(phase = AFTER_COMMIT)` で LINE 通知発火
- **依存タスク:** タスク1, タスク2, タスク6
- **対応 Issue:** [#459](https://github.com/poponta2020/match-tracker/issues/459)

### タスク8: DensukeTemplateController 実装
- [x] 完了（2026-04-17）
- **概要:** テンプレート CRUD API エンドポイント
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/DensukeTemplateController.java`(新規)
- **エンドポイント:**
  - `GET /api/densuke-templates/{organizationId}`
  - `PUT /api/densuke-templates/{organizationId}`
  - 権限: `@RequireRole({ADMIN, SUPER_ADMIN})`
- **依存タスク:** タスク5
- **対応 Issue:** [#460](https://github.com/poponta2020/match-tracker/issues/460)

### タスク9: PracticeSessionController にページ作成エンドポイント追加
- [x] 完了（2026-04-17）
- **概要:** 伝助ページ作成の API エンドポイントを既存 Controller に追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `POST /densuke/create-page` 追加
- **エンドポイント:**
  - `POST /api/practice-sessions/densuke/create-page`
  - 権限: `@RequireRole({ADMIN, SUPER_ADMIN})`
- **依存タスク:** タスク7
- **対応 Issue:** [#461](https://github.com/poponta2020/match-tracker/issues/461)

### タスク10: バックエンドテスト追加
- [x] 完了（2026-04-17）
- **概要:** Service / Controller の単体テスト追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukePageCreateServiceTest.java`(新規)
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeTemplateServiceTest.java`(新規)
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/DensukeTemplateControllerTest.java`(新規)
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/PracticeSessionControllerTest.java` — 作成エンドポイントのテスト追加
- **テスト観点:**
  - バリデーション失敗ケース（練習日0件、venue_match_schedules 不足、既存 URL）
  - densuke.biz 通信モックでの正常系・異常系
  - LINE 通知が呼ばれること／失敗しても作成は成功すること
  - 権限チェック（PLAYER ロールのアクセス拒否）
- **依存タスク:** タスク7, タスク8, タスク9
- **対応 Issue:** [#462](https://github.com/poponta2020/match-tracker/issues/462)

### タスク11: テンプレート編集モーダルのフロントエンド実装
- [x] 完了（2026-04-17）
- **概要:** 伝助管理画面の各団体カードにテンプレート編集モーダルを追加（設計変更: 当初想定の OrganizationSettings タブ追加から**モーダル化**に変更 — OrganizationSettings が実態として「所属団体選択画面」で構造が違ったため）
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/densuke/DensukeTemplateModal.jsx`(新規) — モーダル本体、プレースホルダープレビュー付き
  - `karuta-tracker-ui/src/pages/densuke/DensukeManagement.jsx` — 「テンプレート編集」ボタン追加、モーダル起動
  - `karuta-tracker-ui/src/api/densukeTemplates.js`(新規) — API クライアント
- **依存タスク:** タスク8
- **対応 Issue:** [#463](https://github.com/poponta2020/match-tracker/issues/463)

### タスク12: 伝助ページ作成モーダルのフロントエンド実装
- [x] 完了（2026-04-17）
- **概要:** 伝助管理画面に作成ボタン＋モーダルを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/densuke/DensukePageCreateModal.jsx`(新規) — モーダル本体
  - `karuta-tracker-ui/src/pages/densuke/DensukeManagement.jsx` — ボタン追加・モーダル起動処理
  - `karuta-tracker-ui/src/api/practices.js` — `createDensukePage()` 追加
- **UI 挙動:**
  - 年月選択は現在月＋未来 2 ヶ月まで
  - 作成中: スピナー表示、ボタン無効化
  - 成功: トースト表示＋モーダル閉じ＋カード更新
  - 失敗: モーダル内エラー表示、リトライ可能
  - 既存 URL がある月は作成ボタン非表示
- **依存タスク:** タスク9
- **対応 Issue:** [#464](https://github.com/poponta2020/match-tracker/issues/464)

### タスク13: LINE 通知設定画面への ON/OFF トグル追加
- [x] 完了（2026-04-17）
- **概要:** 既存の LINE 通知設定画面に伝助ページ作成通知の ON/OFF を追加
- **変更対象ファイル:**
  - LINE 通知設定画面（既存） — トグル追加（ファイルパスは画面調査後に確定）
  - 関連 API クライアント — 設定項目の入出力対応
- **依存タスク:** タスク3
- **対応 Issue:** [#465](https://github.com/poponta2020/match-tracker/issues/465)

### タスク14: E2E 動作確認
- [ ] 完了
- **概要:** 開発環境で実動作確認
- **確認項目:**
  - 伝助ページが正しく作成される（タスク1 で特定したエンドポイントが機能する）
  - `densuke_urls` に新レコードが保存される
  - 指定月の全練習日×試合枠が伝助ページ上に並ぶ
  - 時刻は `venue_match_schedules` の値が反映される
  - LINE 通知が対象メンバーに届く（管理者には届かない）
  - ON/OFF 設定の OFF で通知が抑制される
  - 既存の `DensukeSyncScheduler` が新 URL を次回サイクルで読み込む
  - バリデーションエラーケース（0件、不整合、既存 URL）が意図通り動作
- **依存タスク:** タスク10, タスク11, タスク12, タスク13
- **対応 Issue:** [#466](https://github.com/poponta2020/match-tracker/issues/466)

### タスク15: ドキュメント更新
- [x] 完了（2026-04-17）
- **概要:** CLAUDE.md のルールに従い、主要ドキュメントを更新
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 伝助ページ自動作成機能の仕様追加
  - `docs/SCREEN_LIST.md` — 作成モーダル、テンプレート編集タブを追記
  - `docs/DESIGN.md` — 新規テーブル・クラス構成を追記
- **依存タスク:** タスク14（動作確認完了後、実装が確定した内容で更新）
- **対応 Issue:** [#467](https://github.com/poponta2020/match-tracker/issues/467)

## 実装順序

```
タスク1 #453 (伝助仕様解析)
  │
  ├─ タスク2 #454 (densuke_templates テーブル)
  ├─ タスク3 #455 (line_notification_preferences 列追加)
  └─ タスク4 #456 (LineNotificationType enum 追加)
       │
       ├─ タスク5 #457 (DensukeTemplateService) ← タスク2
       │    └─ タスク8 #460 (DensukeTemplateController)
       │         └─ タスク11 #463 (テンプレート編集タブ)
       │
       ├─ タスク6 #458 (LINE 通知メソッド) ← タスク3, タスク4
       │    │
       │    └─ タスク7 #459 (DensukePageCreateService) ← タスク1, タスク2, タスク6
       │         └─ タスク9 #461 (作成エンドポイント)
       │              └─ タスク12 #464 (作成モーダル)
       │
       └─ タスク13 #465 (LINE 設定トグル) ← タスク3
              │
              └─ タスク10 #462 (バックエンドテスト) ← タスク7, タスク8, タスク9
                   │
                   └─ タスク14 #466 (E2E 動作確認) ← タスク10, タスク11, タスク12, タスク13
                        │
                        └─ タスク15 #467 (ドキュメント更新)
```

**並列可能な区分:**
- 第1波: タスク2 #454, 3 #455, 4 #456（DB スキーマ整備、独立並列）
- 第2波: タスク5 #457, 6 #458, 13 #465（Service/Entity 拡張、並列）
- 第3波: タスク7 #459（コア実装、前提依存）
- 第4波: タスク8 #460, 9 #461（Controller、並列）
- 第5波: タスク10 #462, 11 #463, 12 #464（テスト・フロント、並列）
- 第6波: タスク14 #466 → タスク15 #467（最終確認、順次）
