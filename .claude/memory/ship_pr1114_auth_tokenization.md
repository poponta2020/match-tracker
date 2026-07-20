---
name: ship-pr1114-auth-tokenization
description: 認証をヘッダー自己申告からサーバ発行トークンへ全面変更し出荷（PR #1114、Issue #1106・子#1107-1111）
type: project
category: ship
tags: [security, auth, token, bcrypt, deny-by-default, migration, race-condition]
---

# PR #1114 出荷記録 — 認証のトークン化

- PR: https://github.com/poponta2020/match-tracker/pull/1114
- 親 Issue: #1106（子 #1107-1111。PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/features/auth-tokenization/requirements.md`
- 出典: [[ship_pr1112_missing_requirerole_authorization]]（#1104 のヘッダー認証不信の本体対応）
- 出荷日: 2026-07-19

## 何を変えたか

`X-User-Role` / `X-User-Id` 自己申告（`curl -H "X-User-Role: SUPER_ADMIN"` で誰でも全団体操作可）を廃止し、サーバ発行トークンへ。

- **トークン基盤**: 不透明ランダム32バイト→hex64文字。DB は SHA-256 ハッシュのみ保存（生トークン非保存）。有効期限≈1年、パスワード変更・論理削除・ログアウトで失効。`auth_tokens` 表新設（本番適用済）
- **BCrypt 化**: 書き込み4経路（選手作成・更新・招待登録・伝助自動登録）を `PasswordEncoder` に集約。DTO の `toEntity()`/`applyTo()` は**ハッシュ済みを引数で受け取る**形にし、経路追加漏れをコンパイルエラー化
- **deny by default**: `/api/**` は既定で認証必須。公開許可リスト（login・招待検証/登録・`GET /api/organizations` 完全一致・会場予約プロキシ）のみ未認証通過。`@RequireRole` の有無は認可判定のみに使う（付け忘れでも穴が開かない = #1104 再発防止）
- 認証失敗=401（`UnauthorizedException` 新設）、権限不足=従来どおり403
- リクエスト属性 `currentUserId`/`currentUserRole`/`adminOrganizationId` の契約維持で下流185箇所無改修

## auto-review（Codex CLI・effort high）

**R1-R5 で pass 収束（R5 = blockers 0 / should_fix 0）。** 偽陽性ゼロ（プロンプトに「意図された設計」を先回り明記したのが効いた）。

- **R4 で実 blocker 1件**: パスワード変更と並行したログインが失効をすり抜ける競合。ログインが変更前ハッシュで認証成功 → `revokeAllForPlayer` の一括 UPDATE の**後で**トークンを INSERT すると、そのトークンが失効対象外で約1年生き残り AC-12 が破れる。→ `login` の選手取得を `findByNameAndActiveForUpdate`（PESSIMISTIC_WRITE）に変更し選手行で直列化。`LoginPasswordChangeRaceIntegrationTest`（実スレッド2本・`@Transactional` を付けず本当に並行）で **ロックを外すと落ち・付けると通る**ことを確認＝理論でなく実在の競合の裏付け
- R2 は理由付けが誤り（BCrypt 72バイト超は例外でなく黙って切り詰め＝実測）だが隣に本物あり。R3 は自分の R2 修正の退行。**本番実測で指摘の射程を判定する**のが有効だった

## テスト・AC

- BE 1700テスト green（race テスト追加後）／FE 744 green／lint 0
- auto-test の AC-1〜18 は緑。AC-19（本番マイグレーション）適用済。**AC-20（実機ログイン往復）のみ未検証**＝ローカルは本番 DB を指すため実施不可、本番デプロイ後に目視

## 出荷時の要注意（教訓）

- **ロールバック不可の一方通行**: デプロイ後の起動時 `PasswordHashMigrationRunner` が本番175件の平文を BCrypt 化。旧コード（平文 equals）に戻すと**全員ログイン不能**。平文バックアップはローカル（リポジトリ外）に取得済み
- **ローカル起動＝本番書き換え**: ローカルも同じ Render Postgres を指すため、新コードのローカル起動で本番パスワードがその場でハッシュ化される。実機確認は別 DB 必須
- 既存ログイン中ユーザーは全員1回だけ再ログイン必要（`dummy-token` 無効化）
- Render `healthCheckPath=/ping`（`/api/**` 外）で deny by default の影響なし・GitHub Actions の scrape/sync は `pg` 直書きで API 非経由＝影響なし（いずれも確認済み）
