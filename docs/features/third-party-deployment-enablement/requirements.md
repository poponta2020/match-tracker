---
status: completed
design_required: false
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
next_section: -
---
# 配布可能化（初期データseed＋フロント団体汎用化） 要件定義書

> 監査 [docs/audits/third-party-club-deployment-assessment.md](../../audits/third-party-club-deployment-assessment.md) の残ブロッカー **A-2**（初期データ投入SQL不在）と **D-2**（フロントの北大決め打ち）を、「他会が fork-and-deploy して起動し、実際に使える」1ゴールとして束ねて解消する。方針は grill-me（2026-07-21）で確定済み。

## 1. 概要

- **目的**: 別のかるた会が本リポジトリを clone し、別の Render＋別DBで自前運用する際、(1) 空DBに最初の団体・管理者を投入する手段が無い（鶏卵問題）、(2) フロントが `hokudai`/`wasura` を文字列で特別扱いしており他会団体で機能が黙って欠ける、の2点を解消する。
- **背景・動機**: 方針は「新規DB・独立インスタンス」で確定（監査 G章）。スキーマ構築（`database/schema.sql`）と認証・暗号化（S-1〜S-3）は解消済みで、残るのは初期データ投入とフロントの団体汎用化。この2つが揃って初めて「起動して実際に使える」状態になる（A-2 単独では seed した団体でフロントが壊れるため対で必要）。
- **スコープ判断**: A-2（新規seed）と D-2（既存挙動の改修）は層が異なるが、「配布可能化」という単一ゴールに密結合するため意図的に1機能/1PRにまとめる（1PR=1機能の原則からの意図的逸脱。ユーザー決定 2026-07-21）。検証も両者を1周（非hokudai団体をseed→起動→ログイン→フロントに決め打ちが出ない）で同時に担保できる。

## 2. ユーザーストーリー

- **対象ユーザー**: 本アプリを自会で運用したい別のかるた会の運用担当者（＝fork してデプロイする人。技術リテラシーは中程度、psql は使える前提）。
- **目的**: clone → schema 適用 → 初期データ投入 → 起動 → 管理者ログイン、までを詰まらず1周でき、ログイン後は管理画面から自会の団体運用（会場・選手・所属）を組み立てられること。
- **利用シナリオ**:
  1. 空DBに `database/schema.sql` を適用（解消済み）。
  2. `database/seed_initial.sql` の先頭プレースホルダ（団体コード・団体名・管理者名・パスワード）を自会の値に編集し、同じ psql セッションで適用。
  3. アプリを（再）起動 → 起動時に平文パスワードが BCrypt 化される。
  4. 管理者名＋パスワードでログイン → SUPER_ADMIN として全機能にアクセス。
  5. 管理画面から会場・選手を作成、選手に所属団体を割り当て。フロントの各機能（練習参加の締切表示・選手一括編集のクイック追加）が自会団体で正しく動く。

## 3. 機能要件

### 3.1 画面と遷移

新規画面・遷移の追加は無い（design_required: false）。既存画面の挙動を団体非依存に変えるのみ:
- **練習参加画面**（`pages/practice/PracticeParticipation.jsx`）: 締切バナーの表示。
- **選手一括編集画面**（`pages/players/PlayerBulkEdit.jsx`）: 所属団体クイック追加ボタン（行ごと＋一括）。
- （変更しない）団体設定画面（`pages/settings/OrganizationSettings.jsx`）: §5 参照。

### 3.2 ビジネスルール

**A-2 初期データseed**
- seed が投入するのは **最小2行**のみ: `organizations`×1、`players`(role=SUPER_ADMIN)×1。
  - 根拠: 団体作成APIが存在しない（`OrganizationController` に POST 無し）ため org は seed 必須。ログインは `players` 行のみを必要とし所属は空でも成立。会場・追加選手・所属付与はいずれも SUPER_ADMIN が起動後にUIから作成可能。
- `id` はハードコードせず採番に委ねる（`nextval`/IDENTITY のシーケンスずれを避ける）。
- `players` の DB既定値が無い NOT NULL 列（`ical_feed_token`/`created_at`/`updated_at`）は seed が明示設定する（`@PrePersist` は raw SQL で発火しない）。`ical_feed_token` はランダム生成。
- パスワードは**平文で記載してよい**。初回（再）起動で `PasswordHashMigrationRunner` が BCrypt 化する（同ランナーの Javadoc がこの用途を明記）。`require_password_change=true` を立てる（初回ログイン後の変更を促す。ただしFEリダイレクトのみでBE強制ではない＝運用者は自分の秘密値を入れること）。
- **順序制約（最重要）**: seed 適用は**アプリ起動前**に行うか、起動中に適用した場合は**アプリを再起動**する。起動中DBへ後入れして再起動しないと、平文のまま照合され（`BCrypt.matches(平文, 平文格納)=false`）ログイン不能になる。この順序を seed 冒頭コメントと手順記述に明記する。
- 冪等: `organizations.code` / `players.name` の自然キーに `ON CONFLICT DO NOTHING` を付け、再実行を安全にする。
- 陳腐化した `scripts/seed_data.sql`（存在しないテーブル `practice_participations`/`match_results`・存在しない列 `players.username` を参照）を削除する。参照は監査doc のみ（CI・テスト・コードからの参照ゼロを確認済み）。

**D-2 フロント団体汎用化（既存データ駆動・新カラムなし）**
- **締切表示**: 現在は `find(o => o.code === 'hokudai')` の1団体のみ締切を取得。→ ユーザーの所属団体それぞれについて締切設定（`systemSettingsAPI.getDeadline`）を取得し、**設定を持つ（非null）団体について**バナーを表示する。特定コード依存を除去。
- **締切バナーの団体ラベル**: 現在は `（北大）` 固定。→ 対象団体の**略称**で表示する。略称は既存の override＋フォールバック方式を共有ユーティリティ化して用いる（`{wasura:'わすら', hokudai:'北大'}` の override は維持し、未登録団体は `name.substring(0,2)` にフォールバック）。
- **選手一括編集のクイック追加ボタン**: 現在は `hokudai`/`wasura` の2ボタンを決め打ち。→ **団体一覧から動的生成**する（行ごとの `＋<略称>`、一括の `全員に<略称>を追加`）。任意コードの団体で機能する。hokudai のラベルは従来どおり「北大」（略称ユーティリティが保証）。

**本番退行の禁止（両立条件）**
- 上記はすべて**既存 hokudai/wasura の表示・挙動を変えない**追加的汎用化とする。略称 override 維持により hokudai は「北大」、wasura は「わすら」を保持（`name.substring` では "北海道大学かるた会"→"北海" になり退行するため override 撤去は不可）。締切は現状「設定を持つ団体のみ表示」を再現するため、hokudai に設定があれば従来どおり表示・他は非表示。

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | `database/schema.sql` を適用した空DBに `database/seed_initial.sql` を `psql -f`（`ON_ERROR_STOP=1`）で適用してエラー0で完了し、`organizations` 1行・`players`(role=SUPER_ADMIN) 1行が投入される | verify |
| AC-2 | seed 適用→アプリ（再）起動後、seed に平文で書いたパスワードで `POST /api/players/login` が 200＋トークンを返し、`players.password` が BCrypt ハッシュ（`$2[aby]$…`）に変換されている | verify |
| AC-3 | `seed_initial.sql` は `id` をハードコードせず、`ical_feed_token`/`created_at`/`updated_at` を明示設定し、同一SQLを再実行しても `ON CONFLICT` により重複エラーにならない（冪等） | verify |
| AC-4 | `scripts/seed_data.sql` がリポジトリから削除されている | auto-test |
| AC-5 | 締切表示が団体コード `hokudai` に依存せず、締切設定を持つ所属団体について表示される（`code==='hokudai'` 参照が `PracticeParticipation.jsx` から消える） | auto-test |
| AC-6 | 締切バナーの団体ラベルが `（北大）` 固定でなく対象団体の略称で表示される。hokudai は従来どおり「北大」と表示される | auto-test |
| AC-7 | 選手一括編集のクイック追加ボタン（行ごと・一括）が団体一覧から動的生成され、任意コードの団体に対して機能する。hokudai の一括追加ボタンラベルは従来どおり「全員に北大を追加」 | auto-test |
| AC-8 | seed 手順（seed→(再)起動の順序、org code 等のカスタマイズ箇所）が `seed_initial.sql` 冒頭コメントに明記されている | manual |
| AC-9 | 既存のフロントエンドテスト（`PlayerBulkEdit.test.jsx` / `PracticeParticipation.test.jsx` / `PlayerList.bulkEdit.test.jsx` 等）が全て green のまま（本番 hokudai/wasura の挙動非退行） | auto-test |
| AC-10 | lint（`npm run lint`）・フロントテスト一式・バックエンドテスト一式（`./gradlew test`）が成功する | auto-test |

## 5. Non-goals

- **`OrganizationSettings.jsx:29` の `filter(o => o.code !== 'hokudai')` の汎用化**: これは他会団体を消さない**無害な no-op**（消えるのは hokudai だけ。コミット `ebe59a9c` が本番で北大を自己所属トグルから意図的に隠す目的で追加）。汎用化には org への新カラム（例 `auto_managed`）が要り「新カラムなし」方針に反するため今回は放置する。
- **略称マップ `{wasura, hokudai}` の撤去**: override＋フォールバックとして維持（撤去すると本番ラベルが退行）。
- **`organizations` への新カラム追加**（`abbreviation` / `auto_managed` 等）。
- **D-1**（隣室確認・かでる決め打ち／`scripts/room-checker/`・関連Actions）: 別feature（既に fork では不活性のため緊急度低）。
- **A-3**（`LineAdminController` の Vercel URL 外部化・`.env.example`）／**A-4**（README/手順書全面整備）／**D-3/D-4**（ブランディング・docs 2団体前提）: 別項目。ただし本 feature の seed 手順の1周が A-4 手順書の原稿になる。
- デモ用の豊富な seed データ（選手複数・練習日程等）。seed は最小2行のみ。
- **既存本番DBへの seed 適用**: seed は新規fork環境専用テンプレート。現本番には既存データがあり適用しない。

## 6. 技術的制約・契約

- **BE改修なし・DBスキーマ変更なし・本番DBマイグレーション不要**。seed は新規環境用テンプレートSQL、D-2 は純フロント改修。CLAUDE.md の「DBマイグレーション本番適用ルール」は本 feature には**該当しない**（スキーマ変更を伴わないため）。
- **BE は既に団体汎用**（`SystemSetting`＝per-org KVストアが締切で稼働、`Organization` が code/name/color/deadline_type 保持、実行コードに団体決め打ち無し）。D-2 は FE が既に受信済みの団体データで駆動するだけ。
- **本番 hokudai/wasura の表示・挙動を退行させない**（override＋フォールバックで両立。既存テスト green が契約）。
- 略称ロジックは共有ユーティリティ化し `PracticeParticipation` と `PlayerBulkEdit` で再利用（DRY）。
- seed の PW は平文可（起動時ハッシュ化）。**seed→(再)起動の順序**が運用契約。
- 権限: seed が作るのは SUPER_ADMIN 1名。既知プレースホルダPWは同梱しない（`requirePasswordChange` はBE未強制でAPI直ログインを防げないため、運用者が自分の秘密値を入れる）。
- **A-2 の検証は本番到達不要**: 空DBへの schema→seed→起動→ログインを、schema.sql 検証と同様に隔離環境（ローカル/使い捨てPGコンテナ）で1周する。

## 7. 設計判断の根拠

- **A-2 を SQLテンプレートにした理由**: 運用者は schema.sql 適用で既にDBシェルを握っており、直後の `psql -f seed_initial.sql` は追加摩擦ほぼゼロ。Java 起動時自動投入（env駆動）は新規コード・設定面が増え、空判定誤りで締め出し/再投入リスクがあり、schema をシェルで流す以上利点が薄いため不採用。
- **最小2行にした理由**: 団体作成APIが無いため org は必須、ログインは players のみ必要。venue/所属/追加選手はすべて起動後UIで作れる（監査の「4行必須」は過大）。seed が薄いほど陳腐化・ズレのリスクが小さい。
- **D-2 を新カラム無しにした理由**: 締切は `getDeadline` の非null判定、略称は既存 override＋`name.substring` フォールバック、ボタンは org一覧の動的描画で足り、新カラムは A-2 の seed 値と本番マイグレーションを増やすだけで割に合わない（lean方針）。
- **締切の汎用条件を deadlineType でなく「設定の有無」にした理由**: `getDeadline` 非null判定なら現状（hokudai のみ表示）を確実に再現でき、deadlineType の値（テストに `FIXED`/`SAME_DAY`/`MONTHLY` が混在）に依存しないため退行リスクが無い。
