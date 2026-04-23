# 東区民センター かっこう（和室）空き状況スクレイピング調査報告書

**調査日**: 2026-04-23
**対象**: 札幌市東区民センター かっこう（和室） 夜間(18:00-21:00)の空き状況
**対象URL**: `https://sapporo-community.jp/UserWebApp/Form/SsfSvrRoomAvailabilityMonth.aspx`
**ログイン**: 不要
**目的**: タスク3（スクレイピングスクリプト作成）の実装前提情報

---

## 1. 遷移フロー（ログイン不要）

月表示ページは直URL（GET）では `NoneSessionInfo.html`（操作エラー）にリダイレクトされるため、必ず以下の postback チェーンで遷移する必要がある。

| # | URL / 操作 | 備考 |
|---|----------|-----|
| 1 | GET `https://sapporo-community.jp/UserWebApp/Form/UserMenu.aspx` | 直アクセス可 |
| 2 | Click `#ctl00_cphMain_wucImgBtnVacantRoomsSearchLogout_imgbtnMain` | 「空き状況検索（ログインなし）」。遷移先: `SvrSelSearchType.aspx` |
| 3 | Click `#ctl00_cphMain_WucImageButton1_imgbtnMain` | 「施設から検索」。遷移先: `SvrSelFacilities.aspx` |
| 4 | `selectOption("#ctl00_cphMain_WucFacilitySelect_ddlFacilities", "103")` | 札幌市東区民センター。AutoPostBack 発火 |
| 5 | `selectOption("#ctl00_cphMain_wucRoomSelect_ddlRooms", "041")` | かっこう（和室）。AutoPostBack 発火 |
| 6 | Click `#ctl00_cphMain_btFwdRoomSelect` | 部屋単位の月表示へ。遷移先: `SsfSvrRoomAvailabilityMonth.aspx` |

**重要**:
- 手順4・5はドロップダウン選択だけで AutoPostBack が発火するため、`waitForLoadState("networkidle")` を各選択後に入れること
- 手順6に `#ctl00_cphMain_btFwdDaySelect` を押すと別の「日別・全部屋」表示（`SsfSvrRoomAvailabilityDay.aspx`）になるので誤らないこと

## 2. 施設・部屋コード

空き状況検索画面の select から抽出:

| 施設 | コード |
|------|--------|
| 札幌市東区民センター | `103` |

| 部屋 | コード |
|------|--------|
| かっこう（和室）２階 | `041` |
| さくら（和室）２階 | `042` |
| 和室全室２階 | `040` |

## 3. 月表示ページのDOM構造

### 3.1 メインテーブル

```
selector: #ctl00_cphMain_tblMain
class:    dynamicBasicTable
行数:     32（月により増減: ヘッダ2行 + データ28〜31行）
```

### 3.2 列構成（8列）

| Col index | ヘッダ | 時間帯 (row 1) | 備考 |
|-----------|--------|----------------|------|
| 0 | 日付 | — | `<a>dayLinkClick('01')` |
| 1 | 曜日 | — | 水/木/金/土/日/月/火 |
| 2 | 午　前 | 9:00 ～ 12:00 | |
| 3 | 昼食時間 | （空） | 予約不可バッファ |
| 4 | 午　後 | 13:00 ～ 17:00 | |
| 5 | 夕食時間 | （空） | 予約不可バッファ |
| **6** | **夜　間** | **18:00 ～ 21:00** | **本機能のターゲット列** |
| 7 | 延　長 | 21:00 ～ 22:00 | |

データ行は `table.rows[2]` から始まる（row[0]=列ヘッダ、row[1]=時間ヘッダ）。

### 3.3 セルの className による空き状態判別

テキスト（`○`/`×`）だけでなく、className から状態を判別できる。

| className | 意味 | セル内容例 |
|-----------|------|-----------|
| `RoomAvailabilityMonthBodyAvailable` | 利用可 | `<a onclick="newLinkClick(2,'25',4);">○</a>` / `&nbsp;` |
| `RoomAvailabilityMonthBodyReserved` | 予約済 | `<div class="TimeLineCell"><span>×</span></div>` |
| `RoomAvailabilityMonthBodyNA` | 利用不可（対象外時間帯、過去日等） | `&nbsp;` |

**注意**: `Available` 状態でもセルが `&nbsp;` のみで空テキストの場合がある（昼食・夕食バッファ等、本来予約対象外の時間帯）。判定は **className 優先** が安全。

### 3.4 確認された空き記号

今回の調査（令和08年04月）で観測:

| 表示 | 意味 |
|------|------|
| `○` | 利用可（クリックで予約画面へ遷移） |
| `×` | 予約済 |
| 空 (`&nbsp;`) | 利用不可 or バッファ時間帯 |

**未観測（ページ凡例記載）**: `公開抽選中`、`事前預かり可`
→ 凡例にある状態なので、予期せぬ状態を取得した場合の拡張ポイントとして扱うこと。

### 3.5 月ナビゲーション

| ID | 機能 |
|----|------|
| `#ctl00_cphMain_lbtPreviousMonth` | 前月へ移動（postback） |
| `#ctl00_cphMain_lbtNextMonth` | 翌月へ移動（postback） |

翌月への遷移後も URL は `SsfSvrRoomAvailabilityMonth.aspx` のまま（postback でビュー内容のみ更新）。

## 4. タスク3（本番スクレイパ）への抽出ルール推奨

`room_availability_cache` への UPSERT 用最小ロジック:

```js
// 月表示ページに到達後の抽出例
const rows = await page.$$eval("#ctl00_cphMain_tblMain tr", trs => trs.map(tr => {
  const cells = Array.from(tr.cells);
  if (cells.length < 8) return null;
  const dayText = (cells[0].textContent || "").trim();
  if (!/^\d{1,2}$/.test(dayText)) return null;  // データ行のみ
  const nightCell = cells[6];  // 夜間列
  const cls = nightCell.className;
  let status;
  if (cls.includes("Reserved")) status = "×";
  else if (cls.includes("Available") && (nightCell.textContent || "").trim() === "○") status = "○";
  else status = "-";  // NA or unknown
  return { day: parseInt(dayText, 10), status };
}).filter(Boolean));
```

**room_availability_cache への保存**:
```
room_name = "かっこう"
target_date = YYYY-MM-DD (令和→西暦変換)
time_slot = "evening"
status = "○" | "×" | "-"
checked_at = NOW()
```

**月跨ぎ**:
- 1回目: 当月の状態を抽出
- `ctl00_cphMain_lbtNextMonth` をクリック → waitForLoadState → 2回目の抽出

## 5. 運用・実装上の注意

1. **セッション依存**: 月表示ページに直URLで行くと `NoneSessionInfo.html` になる。必ず UserMenu 経由のフロー全体を毎回辿る（Playwright で1ページ目から）。
2. **AutoPostBack**: 施設select・部屋selectは値変更だけでpostbackが走る。`selectOption` 後に `waitForLoadState("networkidle")` 必須。
3. **ステータス判定は className 優先**: `○`/`×` のテキストは装飾に使われているが、className (`Reserved`/`Available`/`NA`) の方がロバスト。
4. **令和和暦**: 月ヘッダに「令和08年04月」表記。年月取得時は変換が必要。月データ自体は日付(1〜31)のみなのでUTC年月はスクレイパ側で保持する。
5. **エラー画面の検知**: `page.url()` が `Html/NoneSessionInfo.html` や `Html/HttpClientError.html` に飛んだ場合は異常終了（次回cron再試行）。

## 6. 調査で使用した一時スクリプト

- `scripts/room-checker/explore-month-view-tmp.js` — 本調査で作成（コミットしない方針）
- `scripts/room-checker/explore-higashi-availability.js` — 既存の調査スクリプト（当初は異なる経由 TopMenu 起点だったが、TopMenu は直アクセスで `HttpClientError` になるため UserMenu 起点に変更）

## 7. タスク3で再確認すべきこと

- 凡例の `公開抽選中` / `事前預かり可` の実体（可能なら別月でサンプル取得）
- 月を跨ぐ際の postback レスポンスの整合（ViewState が持ち回れるか）
- 30日連続アクセス時のレート制限（推奨は既存の Kaderu と同じ30分間隔、`concurrency.group` で多重実行防止）
