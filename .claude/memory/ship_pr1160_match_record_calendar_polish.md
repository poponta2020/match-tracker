---
name: ship_pr1160_match_record_calendar_polish
description: PR#1160出荷（戦績確認カレンダー/戦績確認タブのポリッシュ9項目、純FE・polish専用Issueなし＝親#1142はclosed）。design-prototype.patchクリーン適用。auto-review 2R pass
metadata:
  type: ship
---

PR #1160 出荷（戦績確認カレンダー/戦績確認タブのポリッシュ9項目・[[ship_pr1145_match_record_calendar_tabs]] のフォローアップ）。純 FE（BE/API/スキーマ変更なし）。polish 専用 Issue なし（親 #1142 は calendar-tabs で既に closed）。slug=match-record-calendar-polish。

## 変更（design-prototype.patch を基点 f04d227a にクリーン適用＝5ソース + 2テスト + docs）
- `MatchCalendar.jsx`: 緑見出し帯・試合リストを負マージン `-mx-4/6/8` で全幅化／年月ピッカーに月別試合数バッジ（`countForMonth`）／曜日ヘッダ削除・選択日見出しに曜日（`formatMonthDayDow`＝`7/21(火)`）／相手名級を半角括弧／カレンダーグリッド左右スワイプで月移動（`swipeGesture` 再利用・`touchAction:pan-y`・`swiped` フラグで誤選択抑制）。
- `YearMonthPicker.jsx`: 任意 prop `countForMonth`/`triggerRef` 追加（未指定は従来挙動＝`PracticeList` 非退行）。トグル閉じ修正（外側クリック検出からトリガー除外）。
- `MatchList.jsx`: 緑バーを名前＋級のみに簡素化＋フィルタ/検索をクリームサブバーへ切出し＝両タブでタブ位置 by construction 固定（tab top=53px 不変）。
- `PracticeList.jsx`: `triggerRef` 配線（共有バグ修正）。`utils/matchCalendar.js`: `formatMonthDayDow` 新設。
- docs: `SCREEN_LIST.md` 行7を実挙動に更新（M/D(曜)・級半角括弧・ピッカー月別バッジ・トグル閉じ・グリッドスワイプ月移動）。

## auto-review（[[auto_review_round_pr1160]]）
- 2R 収束（全 medium、R2 pass、累計約142k）。effort=medium 判断: DIFF_LINES=1257 だが約500行は commit 済み design-prototype.patch（コード複製 doc）＋spec＝実コード約350行の純UI（[[ship_pr1145_match_record_calendar_tabs]] の「docs膨張はhigh過大→medium妥当」踏襲）。
- **R1 実find（修正済み）**: 他選手表示 375px で年月フィルタ（中央寄せ）と右端アクション（自分に戻す＋検索）が約6〜44px重なる回帰。→ `isOtherPlayer` 時のみサブバー左寄せ（`justify-start`）、self は中央維持（カレンダー月ラベル座標一致保持）。単一行維持で固定ヘッダ pt offset 不変。
- **教訓**: レイアウト重なりの blocker は幅試算でなく **既存 design プロト（`C:/tmp/design-live` の `DesignCalendarFullBleedPreview.jsx`・全API stub・ゼロBE）を 375px で実測**が最速で決定的（advisor 助言）。screenshot がタイムアウトする本環境では `getBoundingClientRect` 実測が正（self=centerX188≈viewport187.5・他選手=フィルタ右138 vs アクション左242）。Codex 提案の「モバイル別行」は offset 破壊するため不採用。

## テスト・検証
- FE 807 テスト green（`BulkResultInput.swipe.test.jsx` 1件は既知の並列フレークで単独 pass）。lint 0 errors。build 成功。
- `MatchCalendar.test` 級半角化＋ゲスト括弧検証を相手名要素判定に（見出し曜日 (金) 誤マッチ回避）。`matchCalendar.test` に `formatMonthDayDow` 単体追加。
- **実機タッチのスワイプ月移動・縦スクロール非干渉・日セル誤選択なしは宿題（未実施）**。プロトは design-screen round9 で実物承認済み。

## DoD / マージ
- CI: Vercel 2件 pass（このプロジェクトは PR/push の test CI を手動化済み＝test.yml 非自動）。
