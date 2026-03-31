---
status: completed
audit_source: ユーザー直接指定
selected_items: [1]
---
# ホーム画面参加率 改修要件定義書

## 1. 改修概要
- **対象機能:** ホーム画面の参加率TOP3 + 自分の参加率
- **改修の背景:** 現在、参加率が全団体のセッションを対象に計算されている。ユーザーの所属練習会（organization）でフィルタリングし、同じ団体内のメンバーのみで参加率を算出すべき。
- **改修スコープ:** 参加率の団体別フィルタリング + 複数団体所属時の複数セクション表示

## 2. 改修内容

### 2.1 参加率の団体フィルタリング

**現状の問題:**
- `PracticeParticipantService.computeAllParticipationRates` が `findByYearAndMonth` で全団体のセッションを取得
- `playerRepository.findAllActive()` で全プレイヤーを対象にしている
- 結果、異なる団体のメンバーが混在した参加率TOP3が表示される

**修正方針:**
- `computeAllParticipationRates` に `organizationId` パラメータを追加
- セッション取得を `findByYearAndMonthAndOrganizationId` に変更
- 対象プレイヤーを `PlayerOrganizationRepository.findByOrganizationId` でその団体所属メンバーに絞る

**修正後のあるべき姿:**
- 参加率はユーザーの所属団体のセッション・メンバーのみで計算される

### 2.2 複数団体所属時の表示

**現状の問題:**
- 参加率セクションは1つのみ

**修正方針:**
- **1団体所属:** 現状通りセクション1つ（団体名ラベルなし）
- **複数団体所属:** 3セクション表示
  1. **全体**（全所属団体のセッション合算）
  2. **団体A**（団体Aのセッションのみ）
  3. **団体B**（団体Bのセッションのみ）

**修正後のあるべき姿:**
- 複数団体所属ユーザーには団体別 + 全体の参加率が表示される
- 1団体のみの場合は現行UIを維持

## 3. 技術設計

### 3.1 API変更

**`GET /api/home?playerId={playerId}` レスポンス変更:**

現行:
```json
{
  "participationTop3": [...],
  "myParticipationRate": {...}
}
```

改修後:
```json
{
  "participationGroups": [
    {
      "organizationId": null,
      "organizationName": "全体",
      "top3": [...],
      "myRate": {...}
    },
    {
      "organizationId": 1,
      "organizationName": "北大かるた会",
      "top3": [...],
      "myRate": {...}
    },
    {
      "organizationId": 2,
      "organizationName": "わすらもち会",
      "top3": [...],
      "myRate": {...}
    }
  ]
}
```

- 1団体所属時: `participationGroups` は要素1つ（`organizationId` にその団体ID、`organizationName` にその団体名）
- 複数団体所属時: 先頭が全体（`organizationId: null`）、以降団体別

旧フィールド `participationTop3`, `myParticipationRate` は削除。

### 3.2 DB変更
なし

### 3.3 フロントエンド変更

**`Home.jsx`:**
- `participationTop3` / `myParticipationRate` の代わりに `participationGroups` を使用
- `participationGroups` の長さが1の場合: 現行UIをそのまま維持（団体名ラベルなし）
- `participationGroups` の長さが2以上の場合: 各グループに対して団体名ラベル付きで参加率セクションを繰り返し描画

### 3.4 バックエンド変更

**新規DTO: `ParticipationGroupDto`**
- `organizationId` (Long, nullable) — nullは全体合算
- `organizationName` (String)
- `top3` (List<ParticipationRateDto>)
- `myRate` (ParticipationRateDto)

**`HomeDto` 変更:**
- `participationTop3` フィールド削除
- `myParticipationRate` フィールド削除
- `participationGroups` (List<ParticipationGroupDto>) 追加

**`HomeController` 変更:**
- `OrganizationService` を注入
- playerIdから所属団体一覧を取得
- 1団体: その団体のみでTOP3 + myRateを計算し、groupsに1要素
- 複数団体: 全体合算 + 各団体でTOP3 + myRateを計算し、groupsに格納

**`PracticeParticipantService` 変更:**
- `computeAllParticipationRates(int year, int month, Long organizationId)` オーバーロード追加
  - `findByYearAndMonthAndOrganizationId` でセッション取得
  - `PlayerOrganizationRepository.findByOrganizationId` で対象プレイヤーをフィルタ
- `computeAllParticipationRates(int year, int month, List<Long> organizationIds)` 全体合算用オーバーロード追加
  - `findByOrganizationIdInAndYearAndMonth` でセッション取得
  - 各団体の所属メンバーの和集合を対象
- `getParticipationRateTop3` / `getPlayerParticipationRate` も同様にorganizationId対応

## 4. 影響範囲
- `HomeDto` のフィールド変更 → フロントエンドの `Home.jsx` が影響を受ける（同時改修）
- 旧フィールド (`participationTop3`, `myParticipationRate`) を使用する箇所は `Home.jsx` のみ
- 他画面・他APIへの影響なし
- DB変更なし → 破壊的変更なし

## 5. 設計判断の根拠
- **レスポンス構造を `participationGroups` 配列に変更:** 団体数に応じて柔軟にセクション数を変えられる。1団体の場合も配列要素1つで統一的に扱える。
- **全体合算を `organizationId: null` で表現:** 特別な定数を設けず、nullで「全体」を意味させるシンプルな設計。
- **1団体時に団体名ラベルなし:** 大多数のユーザーは1団体所属のため、不要な情報を省いてUIを簡潔に保つ。
