---
name: ship-pr1112-missing-requirerole-authorization
description: 未認証で叩けた状態変更エンドポイント6件に認可を追加して出荷（PR #1112、Issue #1105）
type: project
category: ship
tags: [security, bug-fix, require-role, authorization, regression-test]
---

# PR #1112 出荷記録 — 未認証で叩ける状態変更エンドポイント6件に認可を追加

- PR: https://github.com/poponta2020/match-tracker/pull/1112
- Issue: https://github.com/poponta2020/match-tracker/issues/1105（PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/bugs/1105-missing-requirerole-authorization/requirements.md`
- 出典: [[ship_pr1104_unauthenticated_data_seed]] の Non-goals から切り出した調査（Issue #1105）
- 出荷日: 2026-07-19

## 何を直したか

`RoleCheckInterceptor:42-45` は `@RequireRole` が無いハンドラを素通りさせる（fail-open）。**`SecurityFilterChain` / `@EnableWebSecurity` はリポジトリ全体に存在せず**（grep 0 件）、このインターセプタが唯一の認可層なので、注釈の付け忘れがそのまま未認証公開になっていた。

| 対象 | 付与した認可 |
|---|---|
| `PUT /api/players/{id}` | PLAYER+ ＋ 本人 or SUPER_ADMIN |
| `DELETE /api/matches/{id}` | PLAYER+ ＋ `updateMatch:661-668` と同じ所有者判定を service に複製 |
| `/api/line/**` 4件 | PLAYER+ ＋ 本人 or SUPER_ADMIN |
| `/api/venues` 3件 | SUPER_ADMIN（FE ルートガードと一致） |
| `/api/player-profiles` 3件 | SUPER_ADMIN |
| `PUT /api/bye-activities/{id}` | PLAYER+ ＋ 本人 or ADMIN+ |

Critical は `PUT /api/players/{id}`。`PlayerUpdateRequest` が `password` を含み `PlayerService.login:341` が**平文比較**のため、未認証でパスワードを書き換えて任意アカウント（SUPER_ADMIN 含む）を乗っ取れた。同クラスの `DELETE`/`PUT {id}/role` は SUPER_ADMIN gated で、**この1メソッドだけ抜けていた**。

## [[ship_pr1104_unauthenticated_data_seed]] と判断が逆になった理由

PR #1104 では「`@RequireRole` 付与では塞がらない（ヘッダー自己申告だから）→ 削除が唯一の手段」と判断した。本 PR では逆に `@RequireRole` を付けている。矛盾ではなく**目的が違う**:

- #1104 の対象は**全データ破壊**。誰も使わない開発用機能なので攻撃面をゼロにできた
- 本 PR の対象は**製品機能**。消せないので「既存の全エンドポイントと同じベースライン」に揃えるのが正しい修正。ヘッダー詐称という残存リスクは全エンドポイント共通の既知課題（Non-goals）

**判断基準**: 消せるなら消す。消せないなら周囲と同じ水準に揃える。

## ロールの決め方（新しい権限モデルを発明しない）

FE の既存ルートガードと既存の同等エンドポイントに合わせた。`App.jsx:127,129,137,138` が `/players/:id/edit` と `/venues/*` を `RoleProtectedPage requiredRole="SUPER_ADMIN"` で守っていたので、BE もそれに一致させた。`PUT /api/players/{id}` は `PlayerEdit`（管理者が他人を編集）と `ProfileEdit`（本人が自分を編集）の**両方から呼ばれる**ため、フラットなロールゲートでは不可＝本人チェックとの二段構えが必須だった。

## 調査手法の教訓（再利用価値の本体）

1. **ファイル単位の「変更系マッピング数 vs `@RequireRole` 数」カウント比較は両方向に信用できない**。`@RequireRole` は GET にも付くため、カウントが一致していても漏れる（実際 `MatchController`・`PlayerController` の漏れは事前 grep の「0件ファイル」に入っていなかった）。逆に `OrganizationController` はハンドラ内 `checkPlayerAccess` があり誤検知だった
2. **最強の判定法**: ハンドラが**サービスに呼び出し元識別子を一切渡していない**なら、サービス側は原理的に認可し得ない。サービス本体を読まずに無防備を確定できる
3. 逆に `currentUserId` を渡す系（MatchComment / MentorRelationship / ByeActivity POST）は `entity.getX().equals(null)` が false になり **fail-closed**。既知ベースラインと同等で新規の穴ではない
4. **javadoc は証拠にならない**。ByeActivity update は controller / service 双方の javadoc が「相手側で権限制御している」と書いていたが実際は無検証。`getPlayerIdForActivity`（"Controller側の権限チェック用"）が**呼び出し 0 件のデッドコード**なのが決定的証拠。同型を疑ったら helper の呼び出し数を grep する
5. `@RequireRole` は `@Target(METHOD)` のみ＝クラスレベル付与はコンパイル不能。「クラス/メソッド混在ノイズ」は最初から存在しない

## 実装・テストの教訓

- **`@RequireRole` を足すと `X-User-Id` が必須化**（`extractAndSetUserId(request, true)`）し、ヘッダーを送っていない**既存テストが 403 で落ちる**。今回39箇所への追加が必要だった。認可を足す PR では最初から工数に入れる
- **`@WebMvcTest` でも `RoleCheckInterceptor` は効く**（`WebConfig` が `WebMvcConfigurer`、interceptor が `HandlerInterceptor` で include 対象）。403 の回帰テストは重い統合テストでなく `@WebMvcTest` で書ける。ただし interceptor が使う `PlayerRepository` の `@MockitoBean` が要る
- **`@WebMvcTest({A.class, B.class, ...})` で複数コントローラを1クラスに集約できる** — 6コントローラ横断の回帰テストを1ファイルにまとめた
- 認可テストは**403 だけでなく「サービスが呼ばれていないこと」も検証**する（ハンドラ到達前に落ちなければ意味がない）
- **Java でシグネチャ変更を伴う TDD はコンパイルエラーが先に出て RED が観測できない**。先にシグネチャだけ通す（ロジックなし）→ RED 観測（21件 fail）→ 実装、の順が有効
- `MatchServiceTest` に `OrganizationService` のモックが無く `@InjectMocks` で null だった。**既存テストのモック不足は新しい経路を通すまで顕在化しない**

## レビュー

auto-review-loop **1ラウンドで verdict=pass**（effort=high、blockers 0 / should_fix 0 / nits 0、37,913 tokens）。

**Non-goals を6項目プロンプトに明記**したことで、既知課題（ヘッダー詐称・平文パスワード・Spring Security 未導入・未使用EP・段位自己申告）の偽陽性が**ゼロ**だった。[[ship_pr1083]] の偽陽性多発と対照的で、**前提の明示がそのまま偽陽性の抑制になる**。

## AC の充足と残存リスク

AC-1〜AC-6 は自動テストで充足（回帰テスト `UnauthorizedMutationRegressionTest` 26件＋バックエンド全1672テスト green）。

**AC-7（FE の実機スモークテスト）は未実施のまま出荷**（ユーザー判断）。`/show-app` はこのセッションから起動できなかった。自動テストの正常系はすべてモックした識別子で通しており、**FE→BE のヘッダー伝搬という実経路だけ未観測**。根拠は `api/client.js:20-33` の axios インターセプタが全リクエストに両ヘッダーを付与すること＋対象6件がログイン後の画面からのみ呼ばれること（既存の `@RequireRole` 付き EP が本番稼働している時点で経路自体は実証済みとも言える）。**万一誤っていれば該当機能で全ユーザーが 403** になるので、本番デプロイ後にプロフィール編集・LINE設定・会場編集を目視するのが望ましい。

## 付随事項

- **DB スキーマ変更なし＝本番 DB 適用は不要**
- FE 無改修
- `docs/design/architecture.md` に fail-open の性質と「本人性が絡む場合は `@RequireRole` ＋ ハンドラ内チェックの二段構え」を追記済み
- 残る Non-goals: 認証方式そのもの・パスワード平文保存・`@RequireRole` 付け忘れの fail-closed 化（起動時検証等）・`/api/player-profiles` の削除検討
