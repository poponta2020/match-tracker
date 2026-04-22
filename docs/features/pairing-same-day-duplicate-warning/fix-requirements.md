---
status: completed
audit_source: 会話内での挙動確認（正式な /audit-feature レポートなし）
selected_items: [1, 2]
---

# 対戦組み合わせ画面 当日重複ペア警告表示 改修要件定義書

## 1. 改修概要

### 対象機能
対戦組み合わせ画面（`/pairings` → `PairingGenerator`）の「ペア直近対戦日」表示機能。

### 改修の背景
対戦組み合わせ画面で、その日の他の試合で既に組まれているペアを再度組もうとしたとき、以下2点の問題がある:

1. **警告の視認性が低い**: 当日既に組まれているペアでも、日付 `04/23` の形式で他の履歴と同じ薄いグレーで表示されるだけ。ユーザーが「今日組んだ」ことに気付きにくい。
2. **検知漏れがある**: 検知ロジックが「自分より**前の試合番号**のみ」を対象としているため、試合3を編集中に試合4・試合5で既に組まれているペアは検知されない。ユーザーは「試合3から組むこともある」ため、実運用で取りこぼしが発生する。

### 改修スコープ
- **対象**: 手動/自動組み合わせ画面における「直近対戦履歴（recentMatches）」の表示および生成ロジック
- **対象外**: 自動マッチングの**ペナルティ計算**（`calculatePairScore` / `SAME_DAY_PENALTY_SCORE`）と**除外ロジック**（`getTodayPairings`）。今回触らない。

---

## 2. 改修内容

### 2.1 項目①: 当日重複ペアの警告表示

**現状の問題:**
- [PairingGenerator.jsx:963-970](karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L963-L970)
- 当日のペアも過去のペアも同じスタイル（`text-[#6b7280]` の薄いグレー、日付形式 `MM/DD`）で表示されている。

**修正方針:**
- `recentMatches[0].matchDate === sessionDate`（当日）の場合、`⚠今日` を **赤字太字** で表示する。
- 当日以外の履歴は従来通り `MM/DD` で表示する。
- 右寄せの表示枠幅 `w-12` では「⚠今日」がはみ出す可能性があるため、`w-14`（56px）に拡張する。

**修正後のあるべき姿:**
| ペアの状態 | 表示 | スタイル |
|-----------|------|---------|
| 履歴取得中 | `...` | `text-gray-300` |
| 初対戦 | `初` | `text-[#4a6b5a]` |
| 当日既に組まれている | `⚠今日` | `text-red-600 font-bold` |
| 過去に対戦あり | `MM/DD` | `text-[#6b7280]`（従来通り） |

---

### 2.2 項目②: 当日他試合の検知範囲拡張

**現状の問題:**

バックエンド `MatchPairingService.java` の以下3箇所で、「同日の前の試合番号のみ（`matchNumber > 1` かつ `mp.matchNumber < matchNumber`）」を検知対象としている:

1. **[getPairRecentMatches L385-397](karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java#L385-L397)**
   - 手動で選手を投入した直後、`pair-history` API で呼ばれる（リアルタイム表示用）。
2. **[enrichWithRecentMatches L822-834](karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java#L822-L834)**
   - 画面表示時に既存組み合わせへ recentMatches を付加（`getByDateAndMatchNumber` 等から呼ばれる）。
3. **[autoMatch 内 L524-537](karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java#L524-L537)**
   - 自動マッチング結果 `PairingSuggestion.recentMatches` の生成用。

→ いずれも「前の試合番号」しか見ていないため、試合3編集中に試合4・試合5のペアは検知できない。

**修正方針:**

3箇所とも以下に変更する:
- `matchNumber > 1` の条件を除去（`matchNumber != null` のみで判定）
- `mp.getMatchNumber() < matchNumber` → `!mp.getMatchNumber().equals(matchNumber)` に変更
  - 自分の試合番号だけを除外し、それ以外の全試合番号を検知対象とする。

**修正後のあるべき姿:**
- 試合3編集中に、試合1・試合2・試合4・試合5... で組まれたペアすべてが `recentMatches` に当日日付として入る。
- 試合3自身で組まれているペア（同じ試合番号）は除外される。

---

## 3. 技術設計

### 3.1 API 変更
**なし**。既存の API (`GET /match-pairings/pair-history`、`GET /match-pairings` 等) のリクエスト/レスポンス構造に変更はない。内部ロジックのみ修正。

### 3.2 DB 変更
**なし**。

### 3.3 フロントエンド変更

対象ファイル: [karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx](karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L963-L970)

変更箇所: L963-L970 の表示ブロック。

変更後の擬似コード:
```jsx
<span className="text-xs flex-shrink-0 w-14 text-right">
  {pairing.recentMatches === null
    ? <span className="text-gray-300">...</span>
    : pairing.recentMatches && pairing.recentMatches.length > 0
      ? pairing.recentMatches[0].matchDate === sessionDate
        ? <span className="text-red-600 font-bold">⚠今日</span>
        : <span className="text-[#6b7280]">{pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')}</span>
      : <span className="text-[#4a6b5a]">初</span>
  }
</span>
```

`text-[#6b7280]` は従来のグレー色。親要素の `text-[#6b7280]` は削除し、各分岐で個別に色指定する。

### 3.4 バックエンド変更

対象ファイル: [karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java)

共通の修正パターン（3箇所）:

```java
// 修正前
if (matchNumber != null && matchNumber > 1) {
    List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
    for (MatchPairing mp : sameDayPairings) {
        if (mp.getMatchNumber() < matchNumber) {
            // 履歴マップに当日を追加
        }
    }
}

// 修正後
if (matchNumber != null) {
    List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
    for (MatchPairing mp : sameDayPairings) {
        if (!mp.getMatchNumber().equals(matchNumber)) {
            // 履歴マップに当日を追加
        }
    }
}
```

修正対象:
- L385-L397 (`getPairRecentMatches`)
- L822-L834 (`enrichWithRecentMatches`)
- L524-L537 (`autoMatch` 内)

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響内容 | 影響度 |
|------|---------|--------|
| 対戦組み合わせ画面 (`PairingGenerator`) | 当日ペアが「⚠今日」表示に変わる | 要件通り（意図した変更） |
| 自動マッチング実行後のプレビュー表示 | 提案ペアの recentMatches に他試合の当日ペアも反映される | 要件通り |
| 既存組み合わせの表示（既保存分の再読込） | `enrichWithRecentMatches` 経由で当日他試合のペアが反映される | 要件通り |

### 4.2 破壊的変更の有無
**なし**。
- API のリクエスト/レスポンス構造は不変。
- DB スキーマ変更なし。
- 既存データへの遡及影響なし（表示ロジックと履歴生成ロジックのみ）。

### 4.3 スコープ外だが留意すべき既知の類似挙動

自動マッチングの **ペナルティ計算** 側にも同じ「前の試合番号のみ」条件が残る:
- `getTodayPairings` L727-L740 の除外ロジック（`p.getMatchNumber() < currentMatchNumber`）
- 結果として、自動マッチングで試合3を実行中に試合4・5のペアが再提案される可能性は残る。

→ **今回のスコープ外**（ユーザー承認済）。必要なら別チケットで対応する。

### 4.4 テストへの影響

- [pairingDragLogic.test.js](karuta-tracker-ui/src/pages/pairings/pairingDragLogic.test.js): `recentMatches` を `null` にクリアするテスト → **変更不要**。
- [PairingGenerator.integration.test.jsx](karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx): 表示ロジックに関わるアサーションがあれば要修正（要確認）。
- バックエンドに `getPairRecentMatches` / `enrichWithRecentMatches` / `autoMatch` に対するテストがあるか要確認。あれば「当日の他試合番号（前後問わず）を検知する」ケースを追加。

---

## 5. 設計判断の根拠

### なぜ `⚠今日` の赤字太字なのか
- ユーザーが「この組み合わせは今日すでに組まれている」ことを即座に認識できるよう、視覚的強調が必要。
- 日付表示と区別するため、絵文字 `⚠` + 明示的文言 `今日` を併用。
- 赤字太字は通常の警告表示に使われる定番スタイルで、既存のUI慣習（エラーメッセージ等）と整合する。

### なぜ3箇所すべてを修正するのか
- 同じ画面（`PairingGenerator`）が表示する `recentMatches` は、呼び出し経路によって3箇所のいずれかで生成される。
- 1箇所だけ修正すると、経路によって表示が変わり整合性が崩れる。
- 表示ロジックのみの変更で DB・API スキーマは不変なので、3箇所の同時修正リスクは低い。

### なぜ自動マッチングのペナルティ計算は対象外なのか
- ユーザー要件は「表示」に関するもの。ペナルティ計算の修正は自動マッチングのアルゴリズム挙動を変えるため、別途影響評価が必要。
- 今回は表示のバグ修正に焦点を絞り、スコープを明確化。
