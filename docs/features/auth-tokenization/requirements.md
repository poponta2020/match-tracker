---
status: completed
design_required: false
mode: change
approved_at: 2026-07-18
---

# 認証のトークン化 要件定義書（改修）

## 1. 概要

### 目的
現在の認証は**クライアントが自己申告したヘッダーをそのまま信用している**ため、認証として機能していない。これを、サーバが発行・検証するトークンに置き換え、本人性とロールを偽装できない状態にする。

### 背景・動機
`RoleCheckInterceptor` は権限判定を以下だけで行っている（[RoleCheckInterceptor.java:50](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java)）:

```java
String userRoleHeader = request.getHeader("X-User-Role");
userRole = Role.valueOf(userRoleHeader);
```

このため本番 API に対し `curl -H "X-User-Role: SUPER_ADMIN" -H "X-User-Id: 1"` を投げれば、誰でも全団体の全データを操作できる。付随して:

- **所有者チェックも突破可能**: `MentorRelationship` / `MatchComment` / `ByeActivity` / `MatchCardRecord` 等は「本人か」を Service 層で `currentUserId` により判定しているが、この値は検証されていない `X-User-Id` ヘッダー由来（[RoleCheckInterceptor.java:43-46](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java) の `required=false` 経路）
- **`@RequireRole` 未付与は素通り**: 全 198 エンドポイント中 82 本が `@RequireRole` を持たず、インターセプタを無条件通過する。うち 22 本は**ノーガードの書き込み系**（`PUT /api/players/{id}`、`DELETE /api/matches/{id}` 等）
- **パスワードが平文保存**: [PlayerService.java:341](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerService.java) で `equals` による平文比較

先行して PR #1104（Issue #1103）で未認証の破壊的シードエンドポイント `POST /api/seed/all` を削除したが、これは「ヘッダー認証が信用できない」前提の上での**個別回避**であり、認証基盤そのものは未対応のまま残っている。本改修がその本体にあたる。

### この改修が閉じるもの・閉じないもの ★重要

| | 本改修後 |
|---|---|
| 未ログインの第三者による API 操作 | **閉じる** |
| ヘッダー詐称による他人へのなりすまし・ロール昇格 | **閉じる** |
| DB 漏洩時のパスワード即時露出 | **閉じる** |
| ログイン済み会員が他の会員のデータを見る／書き換える（認可の粒度） | **閉じない**（別Issue。§5 参照） |

本改修は**認証**（あなたが誰かを偽れなくする）であって、**認可**（ログイン済みの人が何をしてよいか）の再設計ではない。例えば `GET /api/home?playerId=` は本改修後もログインさえしていれば他人の `playerId` を渡せる。これを Non-goals に明記し、「認証をトークン化したので権限問題は解決した」と誤読されないようにする。

## 2. 変更の動機と内容（delta）

| | 現行 | 変更後 |
|---|---|---|
| 本人性の根拠 | `X-User-Id` ヘッダー（検証なし） | サーバ発行トークン |
| ロールの根拠 | `X-User-Role` ヘッダー（検証なし） | トークンから解決したユーザーのロール |
| トークン | `dummy-token` 固定文字列（未検証） | サーバ発行・サーバ検証・失効可能 |
| パスワード | 平文保存・平文比較 | BCrypt ハッシュ |
| `@RequireRole` 未付与EP | 無条件通過（82本） | 認証必須（許可リストのみ公開） |

## 3. 変更後の挙動

### 3.1 認証フロー

1. `POST /api/players/login`（公開）に選手名＋パスワードを送る
2. サーバは BCrypt でパスワードを照合し、成功時にトークンを発行してレスポンスに含める
3. FE はトークンを保存し、以降の全リクエストに `Authorization: Bearer <token>` を付与する（**既に FE は同形式で送信済み**。中身が `dummy-token` から本物になるだけ — [client.js:15](../../../karuta-tracker-ui/src/api/client.js)）
4. サーバはトークンを検証し、そこから解決した `playerId` / `role` / `adminOrganizationId` をリクエスト属性 `currentUserId` / `currentUserRole` / `adminOrganizationId` にセットする
5. `X-User-Role` / `X-User-Id` ヘッダーは**送られてきても一切参照しない**

### 3.2 認証の既定（deny by default）

`/api/**` は**既定で認証必須**とし、下記の許可リストのみ未認証で通す。`@RequireRole` の有無で認証要否が決まる現行仕様を廃止する（新規エンドポイントを追加した開発者がアノテーションを付け忘れても、穴が開かない構造にする＝#1104 の再発防止）。

**未認証で通す許可リスト（これ以外は 401）**

| エンドポイント | 理由 |
|---|---|
| `POST /api/players/login` | ログイン（認証前） |
| `GET /api/invite-tokens/validate/{token}` | 招待リンクの検証（認証前） |
| `POST /api/invite-tokens/register` | 招待リンクからの公開登録（認証前） |
| `GET /api/organizations` | **招待登録画面が未ログインで呼ぶ**（[InviteRegister.jsx:42](../../../karuta-tracker-ui/src/pages/InviteRegister.jsx)）。見落とすと登録フローが壊れる |
| `GET /ping` | ヘルスチェック（インターセプタ対象外・現状維持） |
| `POST /api/line/webhook/{lineChannelId}` | LINE 署名検証で保護済み |
| `/api/line-chat-worker/**` | サービストークンで保護済み |
| `/api/venue-reservation-proxy/view`・`/fetch/**` | capability トークンで保護済み |
| `/ical/**` | フィードトークンで保護済み（`/api/**` 外） |

未ログインで API を呼ぶ画面は Login と InviteRegister の2つのみであることを確認済み（`/privacy`・`/terms`・Landing は API 呼び出しなし）。

### 3.3 トークンのライフサイクル

- **有効期限**: 発行から約1年。期限切れトークンでの要求は 401
- **失効（revocation）**: 以下の事象で当該選手の発行済みトークンをすべて無効化する
  - パスワード変更
  - 選手の論理削除
  - ログアウト（当該トークンのみ）
- 401 を受けた FE は `localStorage` をクリアして `/login` へ遷移する（**既存挙動**。[client.js:47](../../../karuta-tracker-ui/src/api/client.js)）

### 3.4 パスワード

- 保存・照合とも BCrypt。既存の最低8文字要件は維持
- 既存会員の平文パスワードは移行時に一括でハッシュ化する。**会員は今まで通りのパスワードでログインできる**（パスワード変更の強制はしない）

## 4. Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | トークン無し／無効／期限切れで保護エンドポイントを呼ぶと 401 を返す | auto-test |
| AC-2 | **`X-User-Role: SUPER_ADMIN` を付けてもトークンが無ければ 401**（ヘッダー詐称が通らない） | auto-test |
| AC-3 | **有効なトークンと矛盾する `X-User-Role` / `X-User-Id` を同時に送っても、トークン由来の主体で判定される**（ヘッダーは完全に無視される） | auto-test |
| AC-4 | リクエスト属性 `currentUserId` / `currentUserRole` / `adminOrganizationId` にトークン由来の値がセットされる | auto-test |
| AC-5 | ログイン成功時にトークンが発行され、レスポンスから取得できる | auto-test |
| AC-6 | 誤ったパスワードでのログインはトークンを発行せずエラーを返す | auto-test |
| AC-7 | §3.2 の許可リスト**以外**の `/api/**` は、`@RequireRole` の有無によらず未認証で 401（deny by default） | auto-test |
| AC-8 | §3.2 の許可リストの各エンドポイントは未認証で従来通り成功する（特に `GET /api/organizations`） | auto-test |
| AC-9 | `@RequireRole` を付けない新規エンドポイントを追加しても、既定で認証必須になる（再発防止の構造テスト） | auto-test |
| AC-10 | パスワードは BCrypt ハッシュで保存され、DB に平文が残らない（**全4経路**: 選手作成・選手更新・招待登録・伝助自動登録） | auto-test |
| AC-11 | 移行前の平文パスワードを持つ会員が、**移行後も同じパスワードでログインできる** | auto-test |
| AC-11b | **選手作成・招待登録・伝助自動登録・パスワード変更で保存したパスワードで、再起動を挟まずログインできる**（移行ランナーに依存しない往復検証。書き込み経路の漏れを検出する） | auto-test |
| AC-12 | パスワード変更を行うと、その選手の発行済みトークンがすべて 401 になる | auto-test |
| AC-13 | 選手を論理削除すると、その選手の発行済みトークンが 401 になる | auto-test |
| AC-14 | ログアウトすると当該トークンが 401 になる | auto-test |
| AC-15 | `OrganizationController` の直接ヘッダー読み（`getHeader("X-User-Role")`）がトークン由来の判定に置き換わっている | auto-test |
| AC-16 | 回帰: SUPER_ADMIN / ADMIN / PLAYER の権限境界が現行と変わらない | auto-test |
| AC-17 | 回帰: ADMIN の団体スコープ挙動（`AdminScopeValidator` / `OrganizationScopeResolver`）が現行と変わらない | auto-test |
| AC-18 | 回帰: 既存テスト・lint がすべて成功する | auto-test |
| AC-19 | 本番 Render PostgreSQL にマイグレーション（トークン表・パスワードハッシュ化）が適用済み | manual |
| AC-20 | 実機で、ログイン → 通常操作 → ログアウト → 再ログイン が通る | verify |

内訳: auto-test 19件 / verify 1件 / manual 1件（計21件）

## 5. Non-goals（今回やらないこと）

- **認可（authorization）の粒度設計**: ログイン済み会員が他人の `playerId` を指定して他人のデータを読める問題（`GET /api/home?playerId=`、`GET /api/matches/player/{playerId}` 等、未保護だった参照系 50 本）。本改修では「ログイン必須」までを担保し、誰が何を見てよいかの設計は別 Issue
- `docs/spec/players-auth.md` に既に「既知の制限」として記載済みの、**読み取り系 API の団体スコープ非対称**（`PracticeSessionController.getSessionById` 等）。既に別 Issue 送りが宣言されており、本改修では触らない
- ログイン試行回数制限・ブルートフォース対策
- パスワードリセット機能の新設（管理者経由の既存フローを維持）
- リフレッシュトークン、多要素認証、OAuth/外部 IdP
- ログイン ID の変更（選手名のまま。メールアドレス化はしない）
- LINE Webhook 署名検証 / iCal フィードトークン / 会場予約プロキシトークン / line-chat-worker サービストークンの方式変更（いずれも既に独自の検証を持つため現状維持）
- `Spring Security` のフィルタチェーン全面導入（既存インターセプタ構成を維持する。BCrypt 実装のみ利用する）

## 6. 技術的制約・契約

### 維持すべき契約
- **リクエスト属性 `currentUserId` / `currentUserRole` / `adminOrganizationId` の契約は維持する**。この属性を読む箇所が 23 ファイル・185 箇所あり、供給元だけをヘッダーからトークンに差し替えることで下流を無改修に保つ
- `@RequireRole` アノテーションによるロール制御の記法は維持する（認証の既定だけを変える）
- FE の `Authorization: Bearer` 送信形式・401 時のリダイレクト挙動は既存のまま流用する
- `LoginResponse` の既存フィールドは削らない（トークンを追加するのみ）

### データ・移行
- 既存 `players.password`（平文）を一括で BCrypt ハッシュへ変換する。平文が DB に揃っているため、二重照合や「ログイン時に随時ハッシュ化」といった移行期の仕組みは不要
- **★二重照合を廃する以上、パスワードの書き込み経路を1つでも漏らすと該当ユーザーが出荷後にログイン不能になる。** 経路は以下の4つ（うち2つは「実際はハッシュ化が必要」というコメント付きで放置されている）。すべてを単一の `PasswordEncoder` チョークポイント経由に集約する:

| 経路 | 場所 | 影響範囲 |
|---|---|---|
| SUPER_ADMIN の選手作成 | `dto/PlayerCreateRequest.java:49` | 新規作成された選手 |
| 選手更新・**パスワード変更フロー** | `dto/PlayerUpdateRequest.java:43` | パスワードを変更した全員 |
| 招待リンクからの公開登録 | `dto/PublicRegisterRequest.java:44` | セルフ登録した会員全員 |
| **伝助からの選手自動登録** | `service/DensukeImportService.java:977`（`"pppppppp"` 固定） | 伝助経由で自動登録される選手 |

- **この漏れは green のまま出荷され得る**（移行済みパスワードだけをテストしていると検出できない）。さらに起動時ランナーが次回再起動で平文を拾ってハッシュ化するため、「再起動したら直る」断続バグに化ける。AC-11b の往復テストで構造的に検出する
- **実装上の注意**: 上記3つの DTO は `toEntity()` 内でパスワードを設定しており、DTO からは `PasswordEncoder` に触れない。ハッシュ化は `toEntity()` の中ではなく**サービス層で行う**（DTO に encoder を注入する設計にしない）
- **DB マイグレーション SQL と entity 変更は同一 PR に含め、本番 Render PostgreSQL にも適用する**（CLAUDE.md の最重要ルール。PR 作成時にユーザーへ明示する）
- 出荷時、既存のログイン中ユーザーは全員 1 回だけ再ログインが必要になる（`dummy-token` が無効になるため）

### 環境制約
- Render 無料プランはスピンダウンする。**インメモリの `HttpSession` は使用不可**（コールドスタートで全員ログアウトになる）。トークンは署名付き（自己完結）か DB 永続のいずれかとする
- 失効要件（AC-12/13/14）があるため、**サーバ側でトークン状態を管理する方式**を採る。DB テーブルの追加を伴う
- 現行インターセプタは保護エンドポイントごとに既に `playerRepository.findById` を実行しているため（[RoleCheckInterceptor.java:85](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java)）、DB 参照型トークンによる追加コストは限定的

### テストへの影響
- テストコードで `X-User-Role` / `X-User-Id` を直接組み立てている箇所が 17 ファイル・270 箇所ある（`MatchPairingControllerTest` 62、`PracticeSessionControllerTest` 36、`RoleCheckInterceptorTest` 25 で全体の約 45%）。ヘッダー組み立てをテストヘルパーに集約してから機械的に置換する

### 未解決の技術論点（→ 技術計画フェーズで解決）
- トークンの生成方式（ランダム不透明トークン＋ハッシュ保存 か 署名付きトークン＋失効テーブル併用か）
- トークン表のスキーマとインデックス、期限切れレコードの掃除方針
- 許可リストの実装位置（インターセプタのパスマッチ か 専用アノテーション）
- ログアウト API の新設要否（現状 FE は localStorage 削除のみ）

## 7. 設計判断の根拠

- **①認証・②ハッシュ化・③未保護EPの保護を1つの改修にまとめた**: ①だけでは `PUT /api/players/{id}` 等 22 本のノーガード書き込みが残り、「認証を直した」と言えない。②はログインの契約変更・移行を伴うため①と同時に行うのが安全（分けると移行を2回行うことになる）
- **deny by default を採る**: 82 本に個別注釈を付けるより穴が残りにくく、将来の新規エンドポイントも自動的に保護される。#1104 の `DataSeedController` は「アノテーション付け忘れ」が本質だったため、構造で防ぐ
- **リクエスト属性の契約を維持する**: 185 箇所の下流を無改修に保つことが、この規模の改修を安全に行える最大の条件
- **失効可能な方式を選ぶ**: 有効期限が約1年と長いため、パスワード変更・退会で即座に締め出せないと実質的な救済手段が無くなる
- **パスワード変更の強制はしない**: 平文が外部へ漏洩した痕跡は確認されておらず、約 67 名全員への周知・問い合わせ対応のコストに見合わない

## 8. 変更履歴

- 2026-07-18: 初版。ヘッダー自己申告認証 → トークン認証への全面変更、パスワードの BCrypt 化、`/api/**` の deny by default 化（理由: ヘッダー詐称により誰でも SUPER_ADMIN として全データを操作できる状態のため）
