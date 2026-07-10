---
status: implemented
issue: 1008
---
# バグ改修要件: 伝助の○マークがキャンセル待ち先頭以外だと△に強制上書きされる

## 再現手順
1. あるセッション・試合番号で、定員に対して十分な空き枠がある状態を作る（例: 定員14、当選7名で7席空き）
2. 待ち行列に複数人（例: waitlist_number=1と2）が登録されている状態にする
3. waitlist_number=2以降の参加者が伝助で自分の欄を○にする
4. 伝助の定期インポート（DensukeImportService の Phase1/Phase3 スクレイプ）が実行される
5. 数分後、対象者の伝助の欄が△に書き換わる（本人は○にしたまま何もしていないのに）

## 期待される動作 / 実際の動作
- 期待: 空き枠が自分の待ち順位までの人数を十分カバーしていれば、待ち行列の先頭でなくてもWONに昇格し、伝助の○マークはそのまま維持される
- 実際: `processPhase3Maru()` のWAITLISTED分岐が「待ち行列の厳密な先頭か」のみで昇格可否を判定しており、先頭でなければ空き枠の有無に関わらず昇格せず、`dirty=true` にして△を伝助へ強制的に書き戻す（毎スクレイプサイクルで繰り返し発生）

## 根本原因
`karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` の `processPhase3Maru()`（WAITLISTED分岐、L449-493）内、`atFrontOfQueue` 判定（L462-466）:

```java
boolean atFrontOfQueue = practiceParticipantRepository
        .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                session.getId(), matchNumber, ParticipantStatus.WAITLISTED)
        .map(front -> front.getId().equals(existing.getId()))
        .orElse(true);
```

厳密に「待ち行列の1番目のレコードそのものか」だけを見ており、空き枠が複数人分あっても2番目以降は永久に昇格できない。`hasVacancy && atFrontOfQueue` が false の場合、L486 で `existing.setDirty(true)` が実行され、次のDensukeWriteServiceの書き戻しサイクルで△（WAITLISTED相当のjoin値2）が伝助へ上書きされる。

2026-07-08マージのPR #1003（`fix/densuke-phase1-maybe-admin-promote`）で、この`processPhase3Maru`（Phase3由来）がSAME_DAY（当日）セッションのPhase1処理にも適用されるようになり、当日中に数分おきのスクレイプで繰り返し△へ上書きされる形で顕在化した。

**実例（2026-07-10）**: session_id=998, match_number=3, player_id=82（武内奏磨）。
- waitlist_number=2（1位はplayer_id=175, waitlist_number=1）
- WON 7名 + OFFERED 0名 / 定員14（空き7席、待ちは4名のみ）
- 2026-07-09 22:43頃に伝助で○を設定していたが、空き枠は十分あるにも関わらず「先頭ではない」ため昇格されず
- 本番ログ: `Phase3-A6: WAITLISTED player 82 set dirty for △ write-back` が 12:01・12:26 に発火（`Densuke change-time drift detected` warning付き, driftMinutes=798）、12:31 の書き戻しで実際に△に変化したことを確認済み

## 修正方針
`atFrontOfQueue` の判定を「厳密な先頭か」から「自分の待ち順位（waitlistNumber）までの人数が空き枠に収まるか」に変更する。

- 空き枠 = `capacity - (wonCount + offeredCount)`（既存のhasVacancy算出をそのまま利用）
- 自分より待ち順位が前（waitlistNumber以下）の active な WAITLISTED件数を数え、その件数が空き枠以下であれば昇格可とする
- これにより、自分より前の人の枠は必ず確保された状態を維持しつつ（公平性を保ったまま）、空きがある限り○にした人が正しく昇格するようになる
- 前の人がまだ○にしていない・WONでなくても、空き枠さえ足りていれば後ろの人を先に昇格させて問題ない（前の人の枠は別途空き枠内に確保されているため、後から○にすれば同様に昇格できる）

## Acceptance Criteria
| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | WAITLISTEDの参加者が伝助○を検知され、かつ自分の待ち順位までの人数が空き枠に収まる場合、WONに昇格し△書き戻しは発生しない（dirty=falseのまま） | auto-test（回帰テスト） |
| AC-2 | 自分より前の待ち順位の人数（自分を含む）が空き枠を上回る場合は、引き続き昇格せずdirty=trueで△書き戻しされる（公平性の維持を確認） | auto-test（回帰テスト） |
| AC-3 | 既存テスト・lint・typecheck がすべて成功する | auto-test |

## Non-goals
- 待ち行列の先頭者へ能動的にOFFERを発行する自動繰り上げフローの新設
- ByeActivityService.evaluatePracticeParticipant の matchNumber=null ゴースト参加レコード残留問題の修正（調査中に発見した別問題。別Issueで対応）
- 待ち行列全体を一括で繰り上げるバッチ処理の新設（今回はスクレイプ検知時の単発判定ロジックの拡張のみ）

## 影響範囲
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`（`processPhase3Maru` のWAITLISTED分岐のみ）
- 既存テスト: `DensukeImportServiceTest.java`, `DensukeImportServicePhaseCoverageTest.java`（新しい昇格条件のケース追加が必要）
- 影響を受けるのはPhase1（SAME_DAY当日）およびPhase3（月内通常）双方の○検知処理（同一メソッドを共有）
