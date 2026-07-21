# implementation-plan — match-record-calendar-polish

純 FE ポリッシュ（BE/API/スキーマ変更なし）。設計成果物は [design-spec.md](design-spec.md) と [design-prototype.patch](design-prototype.patch)（実5ファイル・基点 `f04d227a`）。`/define-feature` は不要（宿題ゼロ・収束済み）。

## 前提
- 変更対象は FE のみ 5 ファイル。DB マイグレーション・本番適用は**不要**。
- patch は基点 `f04d227a`（＝現 local main）に対し `git apply --check` 済み（APPLY_OK）。

## タスク（依存順・直列）

### T1. プロトタイプ差分の適用
- `git apply docs/features/match-record-calendar-polish/design-prototype.patch`（当たらなければ patch を読んで手動移植）。
- 対象: `YearMonthPicker.jsx` / `MatchCalendar.jsx` / `MatchList.jsx` / `PracticeList.jsx` / `utils/matchCalendar.js`。
- ビルド確認（`npm run build` もしくは dev 起動でエラーなし）。`git grep DESIGN-PROTO -- <上記5ファイル>` が 0 件であること。

### T2. テスト更新（design-spec §9 の「テスト影響」に対応）
- `MatchCalendar.test.jsx`: 相手名級表記のアサートを**全角 `（A）`→半角 `(A)`** に更新（`ざきお（A）`・`さとう（B）`・`たなか（A）`・`こばやし（D）` 等）。見出しの `toContain('7/21')` は据え置き（`7/21(火)` で通る）。
- `matchCalendar.test.js`: `formatMonthDayDow` の単体テストを追加（`2026-07-21`→`7/21(火)`、`''`→`''`、月/日の十の位省略も踏襲）。
- `MatchList.tabs.test.jsx`: 戦績確認ヘッダ restructure（年月フィルタをサブバーへ移動・緑バー簡素化）で壊れるアサートを実挙動に合わせて修正（年月/検索/自分に戻す/フィルタ件数の位置・存在）。
- `PracticeList.*.test.jsx`: `YearMonthPicker` をモックしているため原則影響なし（実行して確認）。

### T3. 検証
- `cd karuta-tracker-ui && npm run test`（スワイプ系フレークは `vitest run --no-file-parallelism` で切り分け）と `npm run lint` を green に。
- **実機/ブラウザ確認（design-spec §9 の宿題）:**
  - カレンダー⇄戦績確認でタブ帯・「YYYY年M月」の位置が不変（tab top=53px・年月 centerY≈136）。
  - カレンダーのグリッド左右スワイプで月移動（左=翌月/右=前月）。縦スクロール非干渉・日セル誤選択なし（**実機タッチで確認**）。
  - 年月ピッカーのラベル再タップで閉じる（`/matches` カレンダー・`/practice` 両方）。月別件数バッジ表示。
  - 全幅帯が画面両端に届き横スクロールなし（375px）。

## 影響範囲・非退行
- `YearMonthPicker` は共有（`MatchCalendar`・`PracticeList`）。新 prop（`countForMonth`/`triggerRef`）は**任意**で、未指定の呼び出しは従来挙動。`PracticeList` は `triggerRef` のみ配線（件数バッジは出さない）。
- 統計パネル（`getStatisticsByRank`）・戦績データ取得は不変。BE 契約に触れない。
