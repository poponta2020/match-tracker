---
status: completed
audit_source: 会話内ソースコード調査（2026-05-24）
selected_items: [1]
---

# 伝助インポート時の Venue デフォルト値適用 改修要件定義書

## 1. 改修概要

- **対象機能:** 伝助→アプリの定期同期（`DensukeImportService.findOrCreateSession`）で `practice_sessions` を新規作成・補完する処理
- **改修の背景:**
  - 会話内のソースコード調査で、伝助で練習日が追加された際にアプリ側で自動作成される `practice_sessions` レコードに `capacity`（定員）がセットされず常に `null` のままになっていることが判明。
  - 結果として、定員未設定セッションは [DensukeImportService.java:391](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java#L391) や [L686-687](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java#L686-L687) で「常に満員」扱いされ、当日12:00以降の WAITLISTED→WON 自動昇格や空き枠通知が動作しなくなる。
  - 一方 `Venue` エンティティには `defaultMatchCount` および `capacity` が既に定義済みで、[AdjacentRoomService.java:132](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/AdjacentRoomService.java#L132)・[LotteryService.java:227](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java#L227)・[DensukePageCreateService.java:225](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukePageCreateService.java#L225) など複数箇所で Venue 値を session に反映するパターンが確立されている。インポート経路だけがこのパターンから漏れている。
- **改修スコープ:** `DensukeImportService.findOrCreateSession` の Venue 値適用ロジックの追加（1項目）

---

## 2. 改修内容

### 2.1 項目1: 伝助インポート時に Venue のデフォルト値を practice_sessions に反映する

#### 現状の問題

[DensukeImportService.java:805-858](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java#L805-L858) `findOrCreateSession()`:

- 会場名マッチ時に `venue.getId()` のみ利用し、`venue.getDefaultMatchCount()` / `venue.getCapacity()` を取得していない
- 新規セッション作成時:
  - `totalMatches` = 伝助スケジュールの最大試合番号、なければ既定 3
  - `capacity` = 未設定（null）
- 既存セッション処理時:
  - `venueId` の補完のみ実施
  - `capacity` の補完はなし

#### 修正方針

**新規セッション作成時:**
- 会場名マッチ時:
  - `totalMatches` = `venue.getDefaultMatchCount()` を優先採用
  - `capacity` = `venue.getCapacity()` をそのまま採用（null 許容）
- 会場名マッチしない時:
  - `totalMatches` = 既存ロジック維持（伝助の最大試合番号、なければ 3）
  - `capacity` = null（既存どおり）

**既存セッション処理時:**
- `capacity` が null のセッションに対してのみ、Venue から `capacity` を補完する
- `totalMatches` および `venueId` は触らない（管理者の手動調整値を尊重）
- 補完のトリガーは以下の両ケースに対応する:
  - ケースA: `venueId` が null で、今回会場名マッチで補完される時 → 同時に `capacity` も補完
  - ケースB: `venueId` が既に設定済みだが `capacity` が null の時 → `venueId` から Venue を引き当てて `capacity` を補完

#### 修正後のあるべき姿

- 伝助で練習日が追加された際、自動作成される `practice_sessions` に正しい定員・試合数が即時セットされる
- 管理者が後から定員を手動設定する手間が不要になる
- 当日12:00以降の自動昇格・空き枠通知が、伝助由来セッションでも正常動作する
- 既存セッションで未設定の `capacity` が、次回同期時に自動補完される
- 管理者が手動で設定した `totalMatches` や `capacity` の値は変更されない

---

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし（既存カラム `practice_sessions.capacity` および `venues.capacity` / `venues.default_match_count` を利用）

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `DensukeImportService.java` | `findOrCreateSession()` メソッド (L805-L858) を改修。Venue 値を `totalMatches` および `capacity` に反映する。既存セッションについても `capacity` 補完ロジックを追加。 |
| `DensukeImportServiceTest.java` 等 | 新規ロジックに対応する単体テストを追加（venue マッチ時の capacity/totalMatches 適用、既存セッションの capacity 補完）。 |
| `docs/SPECIFICATION.md` | 伝助同期セクションに「会場マッチ時は venue の defaultMatchCount/capacity が自動適用される」旨を追記。 |
| `docs/DESIGN.md` | 同上の設計記述を追記。 |
| `docs/伝助双方向同期.md` | 同上の補足を追記。 |

#### 実装イメージ（参考）

```java
private PracticeSession findOrCreateSession(DensukeScraper.ScheduleEntry entry, Long organizationId,
                                             Long createdBy,
                                             Map<LocalDate, Integer> maxMatchByDate,
                                             Map<LocalDate, String> venueByDate,
                                             Map<String, Venue> venueNameMap,
                                             Set<String> unmatchedVenueSet,
                                             ImportResult result) {
    Optional<PracticeSession> sessionOpt = practiceSessionRepository
            .findBySessionDateAndOrganizationId(entry.getDate(), organizationId);

    String venueName = venueByDate.get(entry.getDate());
    Venue matchedVenue = (venueName != null) ? venueNameMap.get(venueName) : null;
    if (venueName != null && matchedVenue == null) {
        unmatchedVenueSet.add(venueName);
    }

    if (sessionOpt.isPresent()) {
        PracticeSession session = sessionOpt.get();
        boolean changed = false;

        // ケースA: venueId 補完時、capacity も同時に補完
        if (session.getVenueId() == null && matchedVenue != null) {
            session.setVenueId(matchedVenue.getId());
            if (session.getCapacity() == null && matchedVenue.getCapacity() != null) {
                session.setCapacity(matchedVenue.getCapacity());
            }
            changed = true;
            result.getDetails().add(String.format("%s 会場を補完: %s", entry.getDate(), venueName));
        }

        // ケースB: venueId 既設定 + capacity null → Venue から capacity 補完
        if (session.getCapacity() == null && session.getVenueId() != null) {
            // venueNameMap は名前→Venue なので、id→Venue の引き当てを行う
            Venue venueById = venueNameMap.values().stream()
                    .filter(v -> session.getVenueId().equals(v.getId()))
                    .findFirst().orElse(null);
            if (venueById != null && venueById.getCapacity() != null) {
                session.setCapacity(venueById.getCapacity());
                changed = true;
            }
        }

        if (changed) {
            practiceSessionRepository.save(session);
        }
        return session;
    }

    // 新規作成
    Long venueId = (matchedVenue != null) ? matchedVenue.getId() : null;
    Integer capacity = (matchedVenue != null) ? matchedVenue.getCapacity() : null;
    int totalMatches = (matchedVenue != null && matchedVenue.getDefaultMatchCount() != null)
            ? matchedVenue.getDefaultMatchCount()
            : maxMatchByDate.getOrDefault(entry.getDate(), 3);

    PracticeSession session = PracticeSession.builder()
            .sessionDate(entry.getDate())
            .totalMatches(totalMatches)
            .venueId(venueId)
            .capacity(capacity)
            .organizationId(organizationId)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
    session = practiceSessionRepository.save(session);
    result.setCreatedSessionCount(result.getCreatedSessionCount() + 1);
    result.getDetails().add(String.format("%s 練習日を作成（会場: %s, %d試合, 定員: %s）",
            entry.getDate(), venueName != null ? venueName : "不明",
            totalMatches, capacity != null ? capacity.toString() : "未設定"));
    return session;
}
```

---

## 4. 影響範囲

### 影響を受ける既存機能

- **伝助→アプリ同期処理（`DensukeImportService`）:**
  - 新規作成セッションの初期値が変わる（capacity / totalMatches）
  - 既存セッションの capacity が条件付きで補完される
- **当日12:00以降の WAITLISTED→WON 自動昇格（`DensukeImportService.processPhase3Maru` L388-L408）:** 定員が設定済みになることで、これまでスキップされていた自動昇格が動作するようになる（**意図された改善**）
- **空き枠通知（`sendConsolidatedVacancyNotifications` L681-L708）:** 定員が設定済みになることで、これまでスキップされていた通知が送信されるようになる（**意図された改善**）

### 影響を受けない既存機能

- **手動セッション作成（`PracticeSessionService` 経由）:** 改修対象外
- **`DensukePageCreateService`（アプリ→伝助のページ作成）:** 既に `venue.getDefaultMatchCount()` を利用済み
- **`AdjacentRoomService` / `LotteryService` の Venue→Session 反映:** 既存実装を変更しない
- **DBスキーマ:** 変更なし
- **API:** 変更なし
- **フロントエンド:** 変更なし

### 破壊的変更

- なし
- 新規作成セッションのデフォルト値が変わるが、これは「未設定」から「Venue 由来の値」への変更であり、運用上は改善のみ
- 既存セッションの上書きは「capacity が null の場合のみ」に限定するため、管理者が設定した値は保護される

### 注意事項（DB マイグレーション）

- DB スキーマ変更を伴わないため、`database/` 配下への SQL 追加は不要
- 本番 DB への適用作業も不要

---

## 5. 設計判断の根拠

- **「補完」を「null 時のみ充填」と解釈:** ユーザー確認済み。管理者が手動で設定した値（capacity, totalMatches）を Densuke 同期が上書きすることによる事故を防ぐため、null 値のみを補完対象とする。
- **試合数は Venue 優先:** 伝助スケジュールの最大試合番号は「現時点で参加者が登録されている試合の最大値」でしかなく、Venue 側の値が「管理者が明示的に設定した正の値」であるため、Venue を優先する。
- **既存セッションで venueId は触らない:** 管理者が意図的に会場を変更している可能性があるため、Densuke 由来の名前マッチで上書きしない（既存の補完ロジック踏襲）。
- **ケースB（venueId 既設定・capacity 未設定）も対応:** ユーザー確認済み。「capacity = null なら補完」の方針に従い、venueId 設定済みでも補完する。これにより、運用中に Venue に capacity を追加した場合、次回同期で既存セッションも恩恵を受けられる。
- **マイグレーション SQL なし:** スキーマ変更を伴わないため、`CLAUDE.md` の「DBマイグレーション適用ルール」は適用外。

