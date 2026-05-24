# densuke.biz スケジュール編集 API スパイク調査結果

調査日: 2026-05-24
調査者: Claude (with poponta2020)
テスト用テストイベント: `cd=Mswvm6w4XEYJAXse`, `sd=inlwFgDKZ91XE`
（実装完了後に削除予定）

---

## 1. 全体フロー

```
[GET  /complete?cd=xxx&sd=xxx]   作成完了画面（編集用URLが書かれている）
     ↓ ユーザーは「編集用ページアドレス」を保存
[GET  /edit?cd=xxx&pw=xxx]       管理者用編集メニュー（5つのフォーム）
     ↓ 「候補日程 追加」ボタンクリック → JavaScript editdata('ADDSCHEDULE', id)
[POST /edit2?cd=xxx]              タイプ別編集サブフォーム表示
  payload: typ=ADDSCHEDULE&key=<id>&id=<id>
     ↓ schedule textarea に入力 → 登録するボタン
[POST /update]                    実書き込み
  payload: cd=xxx&id=<id>&postfix=&schedule=<新規日程>
     ↓ 302
[GET  /edit?cd=xxx&pw=xxx]       編集メニュー画面に戻る
```

**重要:** `/update` への直接 POST が動作することを実証済み（`/edit2` の経由は省略可能）。

---

## 2. 重要なURL情報の発見

### 編集用ページのURL形式
- スパイク前の予想: `?cd=xxx&sd=xxx`
- **実際:** **`?cd=xxx&pw=xxx`** （`pw` パラメータに sd 値を入れる）
- これが原因で、過去の手動調査で「URL 不正」と判定していた可能性あり

### Cookie 形式
- Set-Cookie: `<CD>EDT=<SD>` （EDT = Edit、Max-Age 180 日）
- 名前は cd の値そのまま + "EDT" の suffix

---

## 3. 編集ページの5つのフォーム

| フォーム名 | action | 用途 |
|-----------|--------|------|
| `inputform` | `list?cd=...` | 「イベント閲覧に戻る」ボタン |
| `editform` | `edit2?cd=...` | イベント名・説明文の編集（typ で分岐） |
| `memberform` | `editmember?cd=...` | メンバー編集 |
| `dateform` | `editdate?cd=...` | 順番変更・削除 |
| `spform` | `/edit?cd=...&pw=...` | サブメニュー |

### typ 値（editform 経由で使われる）
- `ADDSCHEDULE` → 候補日程追加
- `ADDANSWERER` → 回答者（メンバー）追加
- 他にも編集系の typ がある可能性（未調査）

---

## 4. スケジュール追加 API 仕様

### エンドポイント
- **URL:** `POST https://densuke.biz/update`
- **Method:** POST
- **Content-Type:** `application/x-www-form-urlencoded`

### 必須パラメータ

| name | 内容 | 例 |
|------|------|------|
| `cd` | 公開サイトコード（densuke_urls.url から抽出） | `Mswvm6w4XEYJAXse` |
| `id` | 編集ページ内部ID（編集ページの `<input name="id">` から取得） | `10566891` |
| `postfix` | 日付末尾追加文字列 | `` (空でOK) |
| `schedule` | 追加する日程テキスト（改行区切り） | `5/30(土) あかなら 1試合目\n2試合目` |

### Cookie (推奨)
- `<CD>EDT=<SD>` （`/list` または `/edit` を先に GET して取得）
- スパイク調査では Cookie 無しでも動作したが、安全側として付与すべき

### ヘッダー（推奨）
- `User-Agent: Mozilla/5.0`
- `Referer: https://densuke.biz/edit2?cd=<CD>` または `https://densuke.biz/edit?cd=<CD>&pw=<SD>`
- `Origin: https://densuke.biz`

### レスポンス
- **HTTP 302 Found**
- **Location:** `edit?cd=<CD>&pw=<SD>` （成功）
- **Body:** 空

### 動作検証
- 作成時に登録した日程: `5/24(日) すずらん`, `5/25(月) はまなす`
- POST /update で `5/30(土) あかなら` を追加
- 追加後の list ページ: `5/24, 5/25, 5/30` （末尾追加）✅
- 既存日程・既存参加者データへの影響: なし

---

## 5. 動作確認済みの curl コマンド例

```bash
CD=Mswvm6w4XEYJAXse
SD=inlwFgDKZ91XE
PAGE_ID=10566891

urlenc() { printf '%s' "$1" | od -An -t x1 | tr -d ' \n' | sed 's/../%&/g'; }
NEW_DATES=$(urlenc $'5/30(土) あかなら 1試合目\n2試合目')

curl -X POST "https://densuke.biz/update" \
  -H "Cookie: ${CD}EDT=${SD}" \
  -H "Referer: https://densuke.biz/edit2?cd=${CD}" \
  -H "Origin: https://densuke.biz" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "User-Agent: Mozilla/5.0" \
  --data-raw "cd=${CD}&id=${PAGE_ID}&postfix=&schedule=${NEW_DATES}"
```

期待レスポンス:
```
HTTP/1.1 302 Found
Location: edit?cd=Mswvm6w4XEYJAXse&pw=inlwFgDKZ91XE
```

---

## 6. Java 実装で使うコード骨子（参考）

```java
// 0. (cd 抽出)
String cd = DensukeWriteService.extractCd(densukeUrl.getUrl());

// 1. /list を取得して id (pageId) と Cookie を得る
Connection.Response listResponse = Jsoup.connect("https://densuke.biz/list?cd=" + cd)
        .userAgent("Mozilla/5.0")
        .timeout(10000)
        .execute();
Map<String, String> cookies = listResponse.cookies();
String pageId = extractPageId(listResponse.parse());

// 2. 追加分の schedule 文字列を組み立て (DensukePageCreateService.buildScheduleText を再利用)
String scheduleText = densukePageCreateService.buildScheduleText(
    newSessions, venueMap, scheduleMap).scheduleText();

// 3. POST /update
Map<String, String> formData = new LinkedHashMap<>();
formData.put("cd", cd);
formData.put("id", pageId);
formData.put("postfix", "");
formData.put("schedule", scheduleText);

Connection.Response response = Jsoup.connect("https://densuke.biz/update")
        .data(formData)
        .cookies(cookies)
        .method(Connection.Method.POST)
        .userAgent("Mozilla/5.0")
        .referrer("https://densuke.biz/edit2?cd=" + cd)
        .header("Origin", "https://densuke.biz")
        .followRedirects(false)
        .timeout(10000)
        .execute();

if (response.statusCode() != 302) {
    throw new IOException("伝助 /update への POST が失敗: HTTP " + response.statusCode());
}
```

---

## 7. 未検証事項（将来追加調査）

- **既存日程の編集** (typ=??): スケジュール変更用の typ が存在するはず（編集ボタンが UI にある）
- **削除 API** (`editdate?cd=...` 経由): 順番変更/削除のフォーム構造
- **イベント自体の削除** (`/delete?cd=...&sd=...` のような可能性)
- **連続 push 時の競合動作**: 同時 POST で何が起きるか
- **Cookie 無しでの POST**: スパイク中は Cookie 不要でも動いたが、断続的に成功/失敗する可能性
- **schedule に大量の日付を送った時のサイズ制限**: textarea のサイズ上限

---

## 8. 設計への反映事項

1. **エンドポイント: POST /update を直接叩く** — `/edit2` 経由は不要
2. **id は /list から動的取得** — 既存 `DensukeWriteService.extractPageId()` を流用
3. **Cookie は念のため付与** — `/list` 取得時に得られる `<CD>EDT` を再利用
4. **postfix=空、eventchoice 不要** — 編集時は eventchoice 等の設定は不要
5. **scheduleText は既存 buildScheduleText を流用** — フォーマット一貫性確保
6. **302 Location チェックで成功判定** — `edit?cd=...&pw=...` への redirect で成功扱い
7. **末尾追加の挙動を活用** — 既存 `densuke_row_ids` が破壊されないことが保証される

---

## 9. テストイベントのクリーンアップ

- テストイベント: https://densuke.biz/list?cd=Mswvm6w4XEYJAXse
- 編集用: https://densuke.biz/edit?cd=Mswvm6w4XEYJAXse&pw=inlwFgDKZ91XE
- 削除 API が判明したら削除すること。
- 判明しなければ放置（伝助は無料サービスで自動削除されない可能性あり、運用上の問題はない）。
