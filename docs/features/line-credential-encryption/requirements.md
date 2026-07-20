---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
next_section: 承認
design_required: false
---
# LINE認証情報の暗号化 要件定義書

## 1. 概要

- **目的**: `line_channels` テーブルの `channel_secret` / `channel_access_token` を、アプリケーションレベルで暗号化して保存する。現状は平文保存であり、DB ダンプ・バックアップ・DB 閲覧権限を通じて LINE チャネルの認証情報がそのまま漏れる。
- **背景**:
  - `application.properties:69` に `line.encryption-key=${LINE_ENCRYPTION_KEY:}` が宣言されているが、**このプロパティを読むコードが1行も存在しない**「死んだ設定」。暗号化クラスも存在しない。
  - `entity/LineChannel.java:37,41` のコメントは「（暗号化保存）」だが、実装は素の `String` を `varchar(255)` / `TEXT` にマッピングしているだけで**平文保存**。
  - `docs/requirements/line-notification.md:587-589` は「AES-256-GCM（アプリケーションレベル暗号化）／暗号化キーは環境変数で管理」を必須と明記しており、**仕様と実装が乖離**している。本要件はこの乖離を実装で埋める。
  - 配布可能性調査 `docs/audits/third-party-club-deployment-assessment.md` の S-3。新クラブは `line_channels` が空の状態から始まるため、本コンバータと鍵設定があれば**1行目から暗号化**される。

## 2. ユーザーストーリー

- **運営者（アプリ管理者）** として、LINE チャネルの `channel_secret` / `channel_access_token` が DB 上で暗号化されていてほしい。DB ダンプやバックアップファイル（例: `backup.sql`）が流出しても、LINE 認証情報がそのまま読めない状態にしたい。
- **新しく本アプリを立ち上げるかるた会** として、`LINE_ENCRYPTION_KEY` を1つ設定するだけで、登録する LINE 認証情報が最初から暗号化されるようにしたい。逆に **LINE を使わないかるた会** は、この鍵を設定しなくてもアプリが問題なく起動してほしい。

## 3. 機能要件

UI なし・純バックエンドの改修。画面・遷移の変更はなし。

### 3.1 暗号化の対象と経路
- 対象カラム: `line_channels.channel_secret`, `line_channels.channel_access_token`。
- 書き込み: `LineChannel` エンティティの永続化時（新規登録 `LineChannelService`、webhook URL 更新等）に、上記2フィールドを暗号化して DB へ書く。
- 読み取り: `getChannelSecret()` / `getChannelAccessToken()` 経由（webhook 署名検証・push/reply・monthly quota 取得・group member count など17箇所以上）で、呼び出し側は従来どおり**平文を受け取る**。呼び出し側の改修はしない。

### 3.2 暗号文フォーマットとレガシー平文の共存（最重要のビジネスルール）
- 暗号文は**バージョン接頭辞** `enc:v1:` を付ける。判定は接頭辞の有無で行う（try-decrypt して例外で判別する方式は採らない）。
- **接頭辞なしの値はレガシー平文とみなし、復号せずそのまま返す（パススルー）**。これにより、既存の本番60チャネル（平文）が入ったままでもコンバータを載せた瞬間に壊れず、安全にデプロイできる。
- 書き込みは常に暗号化して `enc:v1:` 付きで保存する。

### 3.3 鍵の扱い（遅延検証）
- 暗号鍵は環境変数 `LINE_ENCRYPTION_KEY`。
- **鍵の検証は遅延させる**:
  - 読み取り時、値が接頭辞なし（平文）なら**鍵不要**でそのまま返す。
  - 読み取り時、値が `enc:v1:` 付きなら鍵で復号する。鍵未設定・復号失敗は**明確な例外**を投げる（null やゴミを返さない）。
  - 書き込み時は常に鍵が必要。鍵が未設定なら**明確な例外で fail-fast** する（空鍵での暗号化や、暗号化のはずが平文で保存されるサイレント劣化を絶対にしない）。
- 結果として、**LINE を使わないクラブ（`line_channels` が空）は鍵未設定でもアプリが正常起動する**（暗号化書き込みも復号読み取りも発生しないため）。

### 3.4 エラー・境界条件
- 鍵未設定 × 暗号化書き込み発生 → fail-fast 例外。
- `enc:v1:` 付き × 鍵誤り/破損 → 復号失敗の明確な例外（サイレントに平文送信しない）。
- null / 空文字の値 → 暗号化・復号ともに素通し（`channel_secret` は NOT NULL だが、コンバータは null 安全に実装する）。

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | 値を暗号化して DB へ書く経路（`convertToDatabaseColumn`）は、`enc:v1:` 接頭辞付きの暗号文を返し、元の平文とは異なる文字列になる | auto-test |
| AC-2 | 暗号化した値を読み戻す経路（`convertToEntityAttribute`）は、元の平文と完全一致する（暗号化→復号の往復一致） | auto-test |
| AC-3 | `enc:v1:` 接頭辞を持たないレガシー平文値は、復号されずそのまま平文として返る（パススルー） | auto-test |
| AC-4 | 復号した `channel_secret` を用いた webhook 署名検証（HmacSHA256）が、暗号化前と同一の検証結果になる | auto-test |
| AC-5 | 復号した `channel_access_token` が、push/reply 等の送信呼び出しに**平文で**渡る（Bearer に平文トークンが入る） | auto-test |
| AC-6 | `LINE_ENCRYPTION_KEY` 未設定でも、レガシー平文の読み取りは鍵なしで成功する（LINE 未使用クラブがコンバータ搭載後も起動・動作できる不変条件） | auto-test |
| AC-7 | `LINE_ENCRYPTION_KEY` 未設定で暗号化書き込みが発生した場合、明確な例外で fail-fast し、空鍵暗号化・平文サイレント保存をしない | auto-test |
| AC-8 | `enc:v1:` 付き暗号文を誤った鍵で復号しようとした場合、明確な例外を投げる（null／ゴミを返さない） | auto-test |
| AC-9 | 32文字の `channel_secret` を暗号化した結果が `varchar(255)` に収まる（スキーマ変更不要の担保） | auto-test |
| AC-10 | 既存の LINE 通知・webhook・broadcast 系テストが引き続き green（回帰 AC） | auto-test |
| AC-11 | 既存テスト・lint がすべて成功する | auto-test |

## 5. Non-goals（今回やらないこと）

- **既存本番60チャネル（北大・わすらの ADMIN3 / GROUP11 / PLAYER46）の平文→暗号化の移行は含めない。** パススルー読み取りで無害に共存させ、移行が必要になった時点で別途「管理された一発実行＋平文バックアップ取得」で行う。
- **起動時マイグレーションランナーによる自動移行は実装しない**（ローカル起動が本番 DB を叩き、鍵不一致で本番 LINE を静かに全断させる罠を避けるため。過去の BCrypt ランナーと同型・こちらはより危険）。
- **`LINE_ENCRYPTION_KEY` の発行・Render への設定はユーザーの作業**（Claude はシークレットを生成・入力しない。全環境で同一値が必要）。
- 鍵ローテーション機構（`enc:v1` の `v` は将来拡張の余地として確保するが、v2 やローテーションは今回スコープ外）。
- フロントエンド改修・DB スキーマ変更・`line-chat-worker` の改修。
- `channel_secret` / `channel_access_token` 以外のフィールド・テーブルの暗号化。

## 6. 技術的制約・契約

- **暗号方式**: AES-256-GCM（認証付き暗号）。IV はレコードごとにランダム生成。
- **鍵の形式**: `LINE_ENCRYPTION_KEY` = **base64 エンコードした32バイト（256bit）**。生成コマンド例: `openssl rand -base64 32`。全環境（Render / ローカル / 将来の新クラブ）で同一値。※この形式は提案。承認時に変えたければ指定してください。
- **暗号文フォーマット**: `enc:v1:` + base64( IV(12バイト) ‖ ciphertext ‖ GCM tag(16バイト) )。32文字の secret でも約90文字に収まり `varchar(255)` に収まる（スキーマ変更なし）。`channel_access_token` は `TEXT`。
- **実装方式**: JPA `AttributeConverter<String,String>`（例: `EncryptedStringConverter`）を `LineChannel.channelSecret` / `channelAccessToken` に `@Convert` で**明示付与**（`autoApply=false`。他の String カラムに誤適用しない）。
- **変更禁止挙動（回帰で守る）**:
  - `getChannelSecret()` / `getChannelAccessToken()` の戻り値契約＝呼び出し側17箇所は従来どおり**平文**を受け取る。
  - webhook 署名検証（`LineMessagingService.verifySignature`）・push/reply・monthly quota・group member count が現状どおり動作する。
- **セキュリティ**: 例外メッセージ・ログに平文シークレットや鍵を出力しない。
- **利用技術**: 標準 `javax.crypto`（AES/GCM/NoPadding）。新規ライブラリ追加なし。
- **既知の前提**: LINE 送信失敗は上位で try/catch され `SKIPPED` に握り潰されるため、「アプリが落ちない＝正常」ではない。テストは暗号化往復・署名検証・平文トークン受け渡しを**積極的にアサート**する。

## 7. 設計判断の根拠

- **接頭辞パススルー方式**を採る理由: コンバータ搭載と既存平文データの移行を分離でき、搭載自体をノーリスクにできる（既存60本を壊さない）。try-decrypt-catch は GCM 認証失敗と単なる平文を確実には区別できず脆いため採らない。
- **遅延鍵検証**の理由: 鍵を起動時必須にすると、LINE を使わないクラブにも鍵設定を強制し、配布調査で確認した「LINE 無しで運用できる」性質を壊す。暗号化書き込み時のみ鍵必須にすればこの性質を維持できる。
- **既存移行を Non-goal**にする理由: 稼働中2団体の本番 LINE に触れる操作は、起動時ランナーで自動化すると本番誤爆リスクが高い。パススルーで安全に共存させ、移行は独立した管理操作として切り離す。
