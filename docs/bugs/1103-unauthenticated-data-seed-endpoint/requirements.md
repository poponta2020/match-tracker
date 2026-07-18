---
status: approved
issue: 1103
---
# バグ改修要件: 未認証で誰でも叩ける破壊的エンドポイント POST /api/seed/all

## 再現手順

1. 本番 API に対して認証ヘッダー無しで `curl -X POST https://<api-host>/api/seed/all` を実行する
2. 200 OK が返り、全対戦記録・全練習日程が消え、全選手のパスワードが `pppppppp` になる

同様に `curl -X POST https://<api-host>/api/seed/venue-schedules` で全会場の試合時間割が消去・再作成される。

## 根本原因

`karuta-tracker/src/main/java/com/karuta/matchtracker/controller/DataSeedController.java` の 2 エンドポイントが認証ガードなしで本番に露出している。

- `POST /api/seed/all`（`DataSeedController.java:47`）
  - `practiceParticipantRepository.deleteAll()` / `matchRepository.deleteAll()` / `practiceSessionRepository.deleteAll()`（:53-55）
  - `player.setPassword("pppppppp")` で全選手のパスワードを既知値に上書き（:59-63）
- `POST /api/seed/venue-schedules`（`DataSeedController.java:238`）
  - `venueMatchScheduleRepository.deleteByVenueId()`（:247）で全会場の試合時間割を消去し、北大固有の会場名前提で再作成

いずれのメソッドにも `@RequireRole` が無く、`interceptor/RoleCheckInterceptor.java:42-45` は「アノテーションが無ければ素通り」。クラスに `@Profile` ガードも無いため本番プロファイルでも Bean 登録される。CORS は防御にならない（curl は CORS を無視する）。

出典: `docs/audits/third-party-club-deployment-assessment.md` S-1

## 修正方針

**`DataSeedController.java` をファイルごと削除する。**

`@RequireRole(SUPER_ADMIN)` の付与では**この攻撃を止められない**。`RoleCheckInterceptor.java:50-67` はロールを `X-User-Role` ヘッダーの自己申告のみで判定しており、署名・トークン検証が一切ない（プロジェクトの「localStorage + ダミートークンのプロトタイプ認証」の帰結）。したがって攻撃者は

```
curl -X POST -H "X-User-Role: SUPER_ADMIN" -H "X-User-Id: 1" https://<api-host>/api/seed/all
```

で素通りする。本バグの脅威モデル（URL を知られた時点で第三者が curl で叩ける）に対してアノテーションは実質ノーガードであり、攻撃面を消す手段は削除のみ。

削除の安全性は調査済み:

- フロントエンドからの参照 0 件（`karuta-tracker-ui/` に `/api/seed` の呼び出しなし）
- テストからの参照 0 件
- 内容が陳腐化しており実質動かない（2026/2-3 月のハードコード日付、北大固有の会場名「中央区民センター」「クラーク会館」、`organizationId` 未設定）

代替として `@Profile("!render")` で本番非登録にする案もあるが、上記のとおり実質動かないコードであり、素直に削除する。

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | `POST /api/seed/all` にリクエストしても成功レスポンスにならず、練習日程・選手パスワードが破壊されない（認証ヘッダー無し／SUPER_ADMIN 詐称ヘッダー付きの両方） | auto-test（回帰テスト） |
| AC-2 | `POST /api/seed/venue-schedules` にリクエストしても成功レスポンスにならず、会場の試合時間割が削除されない | auto-test（回帰テスト） |
| AC-3 | 既存テスト・ビルドがすべて成功する（デグレードなし） | auto-test |

### AC の訂正記録（実測による）

当初 AC-1/AC-2 は「**404** が返ること」としていたが、実測の結果このアプリは存在しないルートに対して **500** を返すことが判明した。Spring の `NoResourceFoundException` が `GlobalExceptionHandler` の `@ExceptionHandler(Exception.class)`（`GlobalExceptionHandler.java:244`）に捕まるためで、**本 PR の変更とは無関係な既存の欠陥**（全ての存在しないルートが 500 になる）。

したがって AC をセキュリティ上の実質要件（＝破壊的処理が実行されないこと）に訂正した。ステータスコードは補助検証（`>= 400`）に留める。理由:

- 旧 `/api/seed/all` は処理途中で例外が出ても try/catch で 500 を返す実装だった。つまり**ステータスだけでは「削除済み」と「実行された上で失敗」を区別できない**（実測でも修正前は 500 だった一方、`deleteAll()` は catch より前に完了していた）
- 特定のステータスに固定すると、将来 `GlobalExceptionHandler` が修正されたときにこの回帰テストが壊れる

「存在しないルートが 404 ではなく 500 を返す」件は別の改善事項として切り出す（Non-goals 参照）。

## Non-goals

- 認証方式そのものの是正（ヘッダー自己申告 → トークン検証・JWT 化）
- 平文パスワード保存の是正（監査 S-2）
- 他コントローラの `@RequireRole` 欠落（LineUser / MatchComment / MentorRelationship / PlayerProfile / Venue）— 別 Issue に切り出す。`LineWebhookController` は署名検証前提の意図的無アノテーションであり誤検知
- 開発用シードデータ投入手段の代替提供（監査 A-2 の範囲）
- 存在しないルートが 404 ではなく 500 を返す既存の欠陥（`GlobalExceptionHandler.java:244` の `@ExceptionHandler(Exception.class)` が `NoResourceFoundException` を拾う）の是正 — 全ルートに影響する挙動変更のため別 PR

## 影響範囲

- 削除: `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/DataSeedController.java`
- 追加: 回帰テスト（`POST /api/seed/all` / `/venue-schedules` が 404 であることを MockMvc で検証）
- **DB スキーマ変更なし** → 本番 DB へのマイグレーション適用は不要
- フロントエンド変更なし
- 本番デプロイ後に初めてエンドポイントが消える（デプロイまでは露出が続く）
