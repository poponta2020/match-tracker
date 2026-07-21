---
status: locked
screen: Home（/ 認証後トップ）
component: karuta-tracker-ui/src/pages/Home.jsx
chosen_direction: 脱カード＋色面ブロッキング＋明朝ディスプレイ＋和紙繊維テクスチャ（washi Anti-Slop）
round: 15
prototype: static-mock（visualize widget: home_washi_final_v15）— live worktree未使用（lean path）
prototype_patch: なし（本 design-spec のブループリントで /implement が実 Home をビルド・/show-app で実機検証）
backend_change: なし（FE のみ。既存 /api/home の DTO をそのまま使う）
---

# Home 脱カード・リデザイン design-spec

> 本アプリの脱AIスロップ・デザイン正典 `docs/design/design.md` の**生きた手本（パイロット）**。ここで確立した言語を design.md が一般化する。Home 固有のピクセル値は本ファイルが正典、横断的トークン・原則は design.md が正典。

## 1. 意図とゴール

- Home は毎日見る主役画面。AIっぽさ（浮いた角丸＋影のカードを敷き詰める・色帯ヘッダー複合カード・Trophy＋丸数字バッジ＋プログレスバー）を脱する。
- **最重要原則（このセッションで最も高くついた学び）: 脱スロップ ≠ ミニマリズム。** 敵は「浮いた角丸＋影の箱」だけ。豊かさ（大きな明朝数字・全幅の色面・和紙テクスチャ・バー）はむしろ推奨。初回の"引き算"案は「地味すぎ」で棄却された。
- 手段: **色面ブロッキング**（緑シェル／深緑ヒーロー／和紙の地で領域を分ける＝境界箱でなく背景色で分ける）＋**明朝ディスプレイ**（日付・順位・数値・見出し）＋**高密度の行**（カードでなくヘアライン背景バー）＋**和紙繊維テクスチャ**。

## 2. 状態インベントリ（Home は状態機械。全状態を網羅する）

`/api/home?playerId=` の `HomeDto { nextPractice, participationGroups, hasPendingOffer, unreadNotificationCount }` を描画する。**バックエンド DTO は不変**。

### 2.1 次の練習ヒーロー（緑ヒーロー・常時表示） ★挙動変更
- **常に描画する**（登録有無・予定有無にかかわらず）。現行の「登録状態で色帯を navy/slate/cream に出し分ける」複合カードは廃止し、**単一の深緑ヒーロー `#33503f` に統一**。
- `nextPractice != null`（既存 API は未登録でも所属団体の次の練習を返す＝`registered` フラグ付き。バックエンド改修不要）:
  - eyebrow: 今日なら `TODAY`、それ以外は `NEXT`（cream `#e8d9c5`・letter-spacing 広め）。右に「M試合目に参加予定 ・ N名」（N名=確定参加者数、§2.2）。未登録なら「N名」のみ。
  - 主表示: 明朝の大きな日付（白・約44px、`7/16` 形式）＋曜日（明朝・小）＋右寄せに会場名・時刻（`10:00 — 12:00`）。
  - CTA 行（ヒーロー下端・上辺ヘアライン `rgba(255,255,255,.16)` 区切り・全幅タップ・cream 文字＋矢印）:
    - 今日 or 登録済み → **「対戦確認画面へ →」**（`/pairings?date=<sessionDate>`。旧「組み合わせを作成」の文言変更・遷移先同一）
    - 未登録 → 「参加登録 →」（`/practice/participation`）
- `nextPractice == null`（所属団体なし or 今後の練習が皆無）: 緑ヒーローは維持し、中に「次の練習の予定はまだありません」を控えめに表示。CTA 行なし。

### 2.2 参加者の人数「N名」 ★内容変更（チップ廃止）
- 現行の参加者チップ（`PlayerChip` 一覧＋キャンセル待ち／キャンセル済みの別セクション）は**全廃**。ヒーロー meta 行の「N名」に集約。
- 「N名」= **確定参加者のみ**（`nextPractice.participants` のうち status が active＝現行 Home の表示フィルタ `status !== WAITLISTED/WAITLIST_DECLINED/CANCELLED/DECLINED` と同一）。
- キャンセル待ち・キャンセル済みは Home から落とす（練習詳細・参加登録画面で確認する前提）。

### 2.3 繰り上げオファーバナー ★撤去
- `hasPendingOffer` の青バナー（「繰り上げ参加のお知らせ」→ /notifications）は **Home から落とす**。通知一覧で確認する前提。

### 2.4 参加率 TOP3（`participationGroups[]`） ★表現変更
- 見出し: 明朝で「◯月の参加率」＋小さく `TOP 3`（taupe）、下にブラウン `#5f3a2d` の短い下線（幅34px・2px）。1回だけ（月見出しは全体で1回）。
- グループごと（`participationGroups` を順に。要素は「全体」＋各団体、または1団体のみ）:
  - サブラベル: `団体名（N試合）`。N試合 = `top3[0].totalScheduledMatches`（月の団体セッション合計・非null で信頼できる）。**1団体所属時は団体名を出さない**（現行の `showLabel = participationGroups.length > 1` 挙動を踏襲。ただし総試合数ラベルは出す）。
  - 各行（高さ28px・**カードでなく行の背景に参加率バーを統合**）: 背景 `linear-gradient(to right, rgba(26,54,84,.13) 0 <rate>%, transparent <rate>%)`。左から 順位（明朝 `#1A3654`・幅15px）｜名前（`#3d2b21`）｜**参加試合数「N試合」（名前直後・`#8a7568`）**｜率%（明朝 `#1A3654`・右寄せ `margin-left:auto`）。
  - 自分の行（`myRate` が top3 圏外のときのみ末尾に追加）: 順位番号は出さず**左に茶ライン `border-left:3px solid #82655a`**、バーは `rgba(130,101,90,.2)`、%とバーは茶 `#82655a`、名前は「あなた」。**"YOU" ラベルは出さない**（名前＋茶ラインで識別）。
- `participationGroups` 空なら参加率セクション全体を非表示。

### 2.5 シェル（不変）
- 上部: **トップバー撤去**（§ 例外3）。緑ヒーローが最上部を占有。
- 下部: 既存の5タブ・ボトムナビ（`Layout`）`#4a6b5a` はそのまま。

## 3. 背景＝和紙繊維テクスチャ（本文の"地"）

- ヒーロー（深緑）は斜線ハッチ `repeating-linear-gradient(45deg, rgba(255,255,255,.032) 0 2px, transparent 2px 7px)` を維持（本文の和紙とは軸・質感を変えメリハリを出す）。
- 本文（cream `#f2ede6`）に**和紙の繊維テクスチャ**を敷く。色むら（面のむら）は無し。要素:
  1. **繊維**: ベジェの短いカール線。白寄りの重み付け（濃い繊維は控えめ）。
  2. **簀の目**: 横4px間隔の細線 `rgba(255,253,248,.05)`。
  3. **糸目**: 縦46px間隔（開始24px）の細線 `rgba(255,253,248,.08)`。
  4. **塵**: 樹皮片の小さな茶斑（少なめ・淡め）。
- **確定パラメータ（v15。344×745 相当あたりの数量。実装ではタイル面積に比例させる）**:
  - 繊維トーン重み: `255,254,250`×7 / `250,247,241`×7 / `246,243,235`×1 / `240,231,216`×1 / `229,216,196`×1 / `221,206,183`×1（白が支配的・濃色は各1）
  - 短繊維: 数860 / len `4.8+rand*12` / alpha `0.30+rand*0.38` / lineWidth 0.7
  - 中繊維: 数286 / len `19.2+rand*25.6` / alpha `0.40+rand*0.40` / lineWidth 0.85
  - 長繊維: 数43 / len `46.4+rand*36.8` / alpha `0.46+rand*0.36` / lineWidth 0.95
  - カール: 制御点を経路33%/66%位置で法線方向に ±`len*0.5*rand` オフセットした3次ベジェ
  - 塵: 数66 / rgb `118,90,64` / alpha `0.13+rand*0.15` / 半径 `0.45+rand*0.8` の点
- **実装方式（★重要・§ 技術メモ参照）**: ランダム canvas を毎マウント描かない。**決定論的な seamless タイル画像に凍結**し `background: #f2ede6 url(tile) repeat` で可変高さの本文を覆う。

## 4. データ要件（すべて既存 DTO 内。追加なし＝visual-only 側の契約）

| 表示 | データ源 | 備考 |
|---|---|---|
| 日付・会場・時刻・TODAY/NEXT・registered・matchNumbers | `nextPractice.*` | 既存。未登録でも返る |
| 「N名」 | `nextPractice.participants` の active 件数 | 現行チップの表示フィルタと同一 |
| 「対戦確認画面へ／参加登録」 | `nextPractice.today` / `registered` | 既存 |
| 団体総試合数「（N試合）」 | `top3[0].totalScheduledMatches` | 非null・全員共通 |
| 各行「N試合」 | `ParticipationRateDto.participatedMatches` | 既存 |
| 率% ・バー幅 | `ParticipationRateDto.rate` | 既存 |
| 自分の行 | `myRate`（top3圏外時） | 既存 |

→ **要件への宿題: なし。** バックエンド改修なし。

## 5. 採用/不採用の決定ログ（要点）

- ❌初回"引き算"脱カード → 地味すぎ。✅色面・明朝・バー・テクスチャで密度を出す方針へ転換。
- ✅ヒーローは深緑 `#33503f`（アプリのメインカラー畳緑 `#4a6b5a` の一段深い同系色）。データ/数値は"墨"の紺 `#1A3654` のまま（緑=chrome／紺=data の役割分担）。
- ✅CTA はヒーロー内のヘアライン区切りアクション行（ベタ塗りボタンは"浮く"ので不採用）。
- ✅トップバー撤去（ユーザー名＋プロフィールアイコン）。ヒーローが最上部へ。色差問題も解消。
- ✅参加率バーは行の背景に統合（バー専用の縦幅ゼロ）→3団体15行でも1画面に収まる。丸数字カラーバッジ・Trophy は廃止。
- ✅和紙は feTurbulence の粒ノイズ（=ただの紙）でなく、**繊維（雲龍紙の楮）を Canvas 描画**。簀の目・糸目・塵・繊維の色幅で"和紙らしさ"。

## 6. 意図的例外（visual-only を超える変更。requirements.md §4/§6 に AC 化）

1. 参加者チップ（名前一覧＋待ち/キャンセルの別セクション）→ **人数「N名」（確定のみ）に集約**。
2. **トップバー（`NavigationMenu`）撤去**（ユーザー名表示・`/profile` アイコン）。プロフィールは**設定→プロフィール**（`SettingsPage.jsx`）で到達可（確認済み）。`NavigationMenu.jsx` は Home 専用のため未使用化。
3. 参加率の分数「X/Y」→ **団体ヘッダー総試合数（Y）＋各行の参加数「N試合」（X）**。
4. CTA 文言変更「組み合わせを作成」→ **「対戦確認画面へ」**（遷移先同一）。
5. 繰り上げオファーバナー **撤去**（Home から落とす）。
6. 次の練習ヒーローの**常時表示・単一デザイン化**（登録状態で色帯を分けない／null 時は空状態）。

## 7. 技術メモ（→ /implement）

- **FE のみ**。`Home.jsx` の描画を全面差し替え。データ取得ロジック（`fetchData`・focus refresh・abort）は温存。`NavigationMenu` 撤去、`PlayerChip`・Trophy 等の import 整理。
- **和紙タイル**: 決定論的（seed 固定）に seamless タイル PNG を1枚生成し `src/assets/` へコミット→ Home 本文の背景に `repeat`。生成はコミットする使い捨てスクリプト（node-canvas 等）で。エッジ跨ぎ要素をラップするか半タイルオフセット法で seam を消す。**毎マウントの Math.random canvas は禁止**（非決定的・性能）。
- フォント: 明朝は `'Hiragino Mincho ProN','Yu Mincho','Noto Serif JP',serif`（システム内蔵。web フォント新規導入なし）。
- 回帰: 参加率の算定・遷移先・データ表示（率/試合数/順位/自分の行）は不変。既存 Home テストは表示要素の変更に追随して更新（チップ→人数、分数→試合数 等）。
