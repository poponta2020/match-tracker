---
status: completed
design_required: false
design_skipped_reason: ユーザー指示で design-screen をスキップ（東🌸拡張は既存画面のボタン状態デルタのみ）
completed_sections: [delta動機, 変更後挙動, 変わらないもの, Acceptance Criteria, 技術制約]
next_section: 技術計画
---
# 隣室確認の軽量化（かでる間引き＋東区民の自動確認廃止・会場拡張の手動化）要件定義書（ドラフト）

## 1. 概要
隣室空き確認まわりを軽量化・整理する改修。2つの独立した塊からなる：

- **A: かでる隣室確認スロットル** — 実行頻度を 30分毎 → 1時間毎＋深夜スキップに間引く（純 cron・挙動不変）。
- **B: 東区民の自動隣室確認を廃止し、会場拡張を手動化** — 東区民（東🌸/venue 6）の自動通知と空きスクレイプを止め、東🌸→東全室の会場拡張を「かっこうを予約済みの管理者が押す純手動アクション」に作り替える（BE＋FE）。

A は low-risk な cron 変更、B は UI を伴う挙動変更。**実装タスクは A/B を分離可能に構成する**（サイズ次第で別 PR にできる）。

## 2. 背景・動機
- 隣室関連は現在すべて30分毎に常時稼働。対象セッションは翌日〜40日先で、深夜に確認する意味がなく GHA 無料枠・アプリ負荷の無駄。
- 東区民は隣室の自動監視・通知を必要としない。会場拡張は「実際にかっこうを予約してから押す」運用で、アプリが web を見に行く必要がない。
- **現行設計の罠**: 東🌸拡張は隣室(かっこう)の空きスクレイプ結果 `expandable`(○/●) に依存する。だが管理者がかっこうを予約すると次回スクレイプで「×（予約済）」となり `expandable=false` → **拡張がブロックされる**。運用と実装が噛み合っていない。スクレイプ非依存の手動化はこの罠も解消する。

## 3. 変更後の挙動

### A. かでる隣室確認スロットル
| 対象 | 現行 | 変更後 |
|---|---|---|
| `AdjacentRoomNotificationScheduler`（アプリ内） | `cron="0 */30 * * * *", zone="Asia/Tokyo"` | `cron="0 0 0,6-23 * * *", zone="Asia/Tokyo"` |
| `.github/workflows/scrape-kaderu.yml` | `'*/30 * * * *'` (UTC) | `'0 0-15,21-23 * * *'` (UTC) |

- 毎時0分に1回（1日48→19回）。JST **1〜5時台スキップ・6:00再開**（= UTC 16〜20 スキップ）。`workflow_dispatch` は維持。

### B. 東区民の自動隣室確認廃止＋会場拡張の手動化

**3つの概念を明確に分離する（現状は `isAdjacentCheckTarget` が全部兼務）:**
1. **通知対象**（自動「残り4人」通知を送る会場）= 新セット `ADJACENT_NOTIFICATION_TARGET_VENUE_IDS = {3,11,4,8}`（**かでる4室のみ**）。
2. **空き検証つき拡張**（スクレイプ空きを確認してから拡張）= かでる。従来どおり。
3. **手動拡張**（予約済み前提で空き検証なしに拡張）= 東🌸(venue 6)。今回新設。

| 対象 | 変更後 |
|---|---|
| `AdjacentRoomNotificationScheduler` のフィルタ | `isAdjacentCheckTarget` → `isAdjacentNotificationTarget`（venue 6 を自動通知対象から除外） |
| `.github/workflows/scrape-higashi-availability.yml` | `schedule:` を削除して cron 実行を止める（`workflow_dispatch` は維持） |
| `AdjacentRoomService.expandVenue`（BE） | **venue 6 のみ**空き検証(`expandable`)を要求せず拡張可にする。確認は UI ダイアログで担保。かでる経路は不変 |
| `PracticeList.jsx`（FE） | venue 6 は隣室 status チップを出さず、`expandable` に依存せず「会場を拡張（東全室に）」を管理者に表示。押下で確認ダイアログ→拡張 |
| `ADJACENT_CHECK_TARGET_VENUE_IDS` | **変更しない**（venue 6 を維持）。これにより `getAdjacentRoomAvailability` が DTO を返し続け、拡張先名(東全室)・定員が確認ダイアログに供給される |

**東🌸拡張の想定 UX（叩き台。詳細は design-screen）:** 東🌸セッションで管理者に「会場を拡張（東全室に）」ボタン → 「かっこうを予約済みですか？定員 X→Y に変更します」確認 → 拡張。隣室 status 表示・予約報告ステップ・scrape 依存は無し。拡張後は既存のキャンセル待ち繰り上げ（定員増）を実行。

## 4. Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| **A. かでるスロットル** | | |
| AC-A1 | `AdjacentRoomNotificationScheduler` の `@Scheduled` cron が JST基準で 1〜5時台に発火せず 0,6,7…23時に毎時発火する（アノテーション値を反映） | auto-test |
| AC-A2 | `scrape-kaderu.yml` の schedule cron が `0 0-15,21-23 * * *`(UTC) に更新され `workflow_dispatch` が残る | manual |
| **B. 東区民 rework** | | |
| AC-B1 | 東🌸(venue 6) セッションは自動隣室通知の対象外（scheduler が処理しない）。かでる4室は従来どおり通知対象 | auto-test |
| AC-B2 | `scrape-higashi-availability.yml` の `schedule:` が削除され cron 実行されない（`workflow_dispatch` は維持） | manual |
| AC-B3 | 東🌸の会場拡張が隣室空き状況(scrape)に依存せず実行できる（空き「不明」でも管理者操作＋確認で拡張成立） | auto-test(BE) / verify(FE) |
| AC-B4 | 東🌸拡張後、定員が東全室の定員に更新され、既存のキャンセル待ち繰り上げが走る | auto-test |
| **回帰（必須）** | | |
| AC-R1 | かでる和室の拡張フロー（予約報告→空き検証→拡張）が不変。`expandVenue` のかでる経路は空き検証を引き続き要求する | auto-test |
| AC-R2 | 既存の隣室通知ロジック（残り4人閾値・通知対象=対象団体ADMIN+SUPER_ADMIN・重複排除・文言・夜間空き判定）がかでるで不変 | auto-test |
| AC-R3 | 既存テスト・lint・build がすべて green（新仕様に合わせ既存テストを改修した上で） | auto-test |

## 5. Non-goals（今回やらないこと）
- 予約→練習日 sync の cron 停止・東区民手動ボタン ← **feature ③**
- 取り込み通知の宛先拡大 ← **feature ②**
- **かでる**の拡張・通知の挙動変更（頻度以外）
- 東区民の**予約 sync**（`sync-higashi-reservations.yml`）の変更（別 cron。feature ③ で扱う）
- scrape スクリプト（`sync-to-db.js`／`sync-higashi-availability-to-db.js`）の DOM 抽出ロジック変更
- 隣室ペア定数（`ROOM_MAP`／`ADJACENT_ROOM_NAMES`／`EXPANDED_*`）の変更。`ADJACENT_CHECK_TARGET_VENUE_IDS` から venue 6 を外すことはしない
- `DensukeSyncScheduler`（5分毎・伝助）等、隣室以外のスケジューラ

## 6. 技術的制約・契約
- **変更禁止（回帰で担保）**: かでるの隣室通知・会場拡張は頻度以外すべて不変。`expandVenue` は共有メソッドで、venue 6 branch のみ空き検証をスキップ、かでるは byte-unchanged。
- `ADJACENT_CHECK_TARGET_VENUE_IDS` は venue 6 を維持（`getAdjacentRoomAvailability` の DTO＝拡張先名・定員が確認ダイアログの供給源）。通知除外は新セット `ADJACENT_NOTIFICATION_TARGET_VENUE_IDS` で実現し、`isAdjacentCheckTarget` は据え置く。
- GHA cron は UTC 固定。JST 1〜5 スキップ = UTC 16〜20 スキップに換算。アプリ内は `zone="Asia/Tokyo"` で JST 直接指定。
- **既存テストの改修が必要（申し送り）**: `AdjacentRoomNotificationScheduler{Test,IntegrationTest}`（venue 6 が通知されない）・`AdjacentRoomServiceTest`（venue 6 拡張経路）・`AdjacentRoomConfigTest`・`AdjacentRoomFlow.test.jsx`・`docs/features/higashi-adjacent-room-check` 系テスト（除去する挙動を assert している可能性大）。
- docs 更新: `docs/features/adjacent-room-check/`・`higashi-adjacent-room-check/` の cadence 記述と東区民の自動確認廃止を実装と同一 PR で in-place 更新し、各 `## 変更履歴` に追記。

## 7. 設計判断の根拠
- **cron で止める（スクリプト内ガードにしない）**: 目的が実行回数削減なので起動自体を止める。スクリプト内 early-return では GHA 分が消費される。
- **東🌸拡張をスクレイプ非依存に**: 「予約してから押す」運用に合致し、reserve→×→blocked の罠も解消。スクレイプを残す案（Option 1）は罠が残り軽量化も中途半端。
- **venue 6 を check-target に残し通知セットを分離**: DTO・拡張先マッピング・確認ダイアログを壊さずに自動通知だけを外す最小侵襲。
- **A/B を分離可能に**: A は low-risk な cron、B は UI 変更で risk profile が逆。trivial な A が design 重い B のレビューで人質に取られないようタスク/PR を割れる構造にする。

## デザインへの宿題（→ /design-screen adjacent-room-check-lightweight）
- 東🌸セッションの拡張 UI デルタ：隣室 status チップを消し、「会場を拡張（東全室に）」ボタン＋確認ダイアログのボタン状態を確定する。**既存画面のボタン状態デルタのみ**（新画面ではない）。かでるの隣室 UI は不変。
