# densuke.biz 新規ページ作成 API 仕様（タスク1 調査結果）

調査日: 2026-04-17
調査方法: curl による HTTP リクエスト検証
テスト作成したイベント: `cd=wAeAQgkBpAAgeMrK`, `sd=inNwg4mqS2.wk` （削除予定）

---

## 1. 画面フロー

```
[GET  /event]      フォーム表示
     ↓ 入力
[POST /confirm]    確認画面（クライアント側バリデーション後に自動送信）
     ↓ 確認
[POST /create]     実作成、302 で /complete にリダイレクト
     ↓ 302
[GET  /complete?cd=xxx&sd=xxx]  作成完了画面
     
以降:
[GET  /list?cd=xxx]  出欠表ページ（既存 DensukeScraper のターゲット）
```

**重要:** **/create に直接 POST できる**ことを検証済み。`/confirm` を経由せずとも作成可能。アプリ実装では `/create` を 1 回叩けば完了。

---

## 2. /create エンドポイント仕様

### リクエスト
- **URL:** `https://www.densuke.biz/create`
- **Method:** POST
- **Content-Type:** `application/x-www-form-urlencoded`
- **認証:** **不要**（匿名作成可能）
- **Cookie:** 不要（作成時点では未発行）

### 必須ヘッダー（念のため付与推奨）
- `User-Agent`: 通常のブラウザ UA
- `Referer`: `https://www.densuke.biz/confirm` または `https://www.densuke.biz/event`
- `Origin`: `https://www.densuke.biz`

### フォームフィールド

| name | 必須 | 型 | 説明 |
|---|---|---|---|
| `eventname` | ✅ | text (max 250) | イベント名 |
| `schedule` | ✅ | textarea | **候補日程。1行1候補で改行区切り** |
| `explain` | 任意 | textarea | イベント説明文（出欠登録ページ先頭に表示） |
| `email` | 任意 | text (max 250) | 主催者メアド（登録内容の控えが送られる）|
| `pw` | ✅ | radio (0/1) | パスワード設定 0=なし, 1=あり |
| `password` | 条件付き | text (max 8) | pw=1 のとき必須、英数字8文字以内 |
| `eventchoice` | ✅ | radio (1/2/3) | 回答選択肢 1=`○△×`, 2=`○×`, 3=`◎○△×` |
| `postfix` | 任意 | text | 日付末尾に一括追加する文字列（通常空で可）|

### レスポンス
- **HTTP 302 Found**
- **Location ヘッダー:** `complete?cd=<CD>&sd=<SD>`
  - 例: `complete?cd=wAeAQgkBpAAgeMrK&sd=inNwg4mqS2.wk`
- **Set-Cookie:** `<CD>EDT=<SD>; expires=...; Max-Age=15552000`
  - Cookie 名は「CD + "EDT"」= 編集用 (Edit) セッション
  - Max-Age: 180 日

### cd / sd の役割
- **cd**: 公開用サイトコード（16文字の英数字大小混在）。`/list?cd=xxx` で誰でも出欠表にアクセス可能
- **sd**: 管理者用シークレット（13文字、英数字+ドット）。編集・削除時に必要

---

## 3. UTF-8 エンコーディングの注意点 ⚠️

Windows Git Bash 環境の curl `--data-urlencode` で**日本語が正しく送信されない問題**が発生した。具体的には、日本語を含むフィールドがサーバー側で「空」として受信される。

**原因（推定）:** Git Bash の curl が --data-urlencode でシェルの codepage 変換をかけるなどで UTF-8 バイト列が崩れる。

**回避策:** 手動で UTF-8 バイト列を `%XX` 形式に変換してから `--data-raw` で送信すれば動作する。

```bash
# urlenc 関数定義
urlenc() { printf '%s' "$1" | od -An -t x1 | tr -d ' \n' | sed 's/../%&/g'; }

EVENTNAME=$(urlenc "イベント名")
curl -X POST --data-raw "eventname=${EVENTNAME}&..." https://www.densuke.biz/create
```

**Java 実装での影響:**
Java の標準ライブラリ（`java.net.http.HttpClient`, Spring `RestTemplate`/`WebClient`, Apache HttpClient）は内部で UTF-8 の URL エンコーディングを正しく処理するため、**この問題は Java 実装では発生しない**。`URLEncoder.encode(value, StandardCharsets.UTF_8)` を使えば OK。

---

## 4. 候補日程（schedule）のフォーマット

### 伝助での入力形式（自由書式）
- 1行 1 候補
- 改行区切り
- 例:
  ```
  7/27(金) 20:00～
  7/28(土) 19:00～
  7/30(月) 20:00～
  ```

### match-tracker での組み立て要件 ⚠️

既存の [`DensukeScraper.java`](../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScraper.java) は以下の正規表現で日程ラベルをパースする:

- `DATE_PATTERN`: `(\d{1,2})/(\d{1,2})\([^)]+\)` — 月/日(曜)
- `MATCH_PATTERN`: `(\d+)試合目`
- `VENUE_PATTERN`: `\([^)]+\)(.+?)[\s\u3000]+\d+試合目` — 曜日の後〜「N試合目」の間を会場名と認識

つまり、伝助ページ側の各行は**以下のフォーマットで組み立てる必要がある**:

```
{M}/{D}({曜日}) {HH:MM}～{会場名} {N}試合目
```

**具体例（1日3試合の場合）:**
```
4/20(月) 17:20～すずらん 1試合目
4/20(月) 18:45～すずらん 2試合目
4/20(月) 20:10～すずらん 3試合目
4/22(水) 17:20～はまなす 1試合目
4/22(水) 18:45～はまなす 2試合目
```

### アプリ側の実装方針
- 対象月の `practice_sessions` を全件取得
- 各セッションについて、`total_matches`（未設定時は `venues.default_match_count`）の回数ループ
- 試合ごとに `venue_match_schedules` の `start_time` を時刻として使う
- 曜日は Java の `DayOfWeek` から日本語短縮名にマッピング（日/月/火/水/木/金/土）
- 複数の練習日を改行で連結して `schedule` パラメータに入れる

---

## 5. 動作検証済みの curl コマンド例

```bash
urlenc() { printf '%s' "$1" | od -An -t x1 | tr -d ' \n' | sed 's/../%&/g'; }

EVENTNAME=$(urlenc "2026年5月練習出欠")
SCHEDULE=$(urlenc $'5/5(火) 17:20～すずらん 1試合目\n5/5(火) 18:45～すずらん 2試合目')
EXPLAIN=$(urlenc "5月の練習日程")

curl -X POST \
  -H 'User-Agent: Mozilla/5.0' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'Origin: https://www.densuke.biz' \
  -H 'Referer: https://www.densuke.biz/confirm' \
  --data-raw "postfix=&eventname=${EVENTNAME}&schedule=${SCHEDULE}&explain=${EXPLAIN}&email=&pw=0&password=&eventchoice=1" \
  -i \
  https://www.densuke.biz/create
```

**期待レスポンス:**
```
HTTP/1.1 302 Found
Location: complete?cd=<16文字>&sd=<13文字>
Set-Cookie: <cd>EDT=<sd>; ...
```

レスポンスボディは空。`cd` は Location ヘッダーからパース。

---

## 6. Java 実装で使うコード骨子（参考）

```java
// HttpClient でのリクエスト組み立て
Map<String, String> fields = new LinkedHashMap<>();
fields.put("postfix", "");
fields.put("eventname", title);
fields.put("schedule", scheduleText);  // 改行区切りの日程文字列
fields.put("explain", description);
fields.put("email", contactEmail != null ? contactEmail : "");
fields.put("pw", "0");
fields.put("password", "");
fields.put("eventchoice", "1");

String body = fields.entrySet().stream()
    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
    .collect(Collectors.joining("&"));

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://www.densuke.biz/create"))
    .header("Content-Type", "application/x-www-form-urlencoded")
    .header("User-Agent", "Mozilla/5.0")
    .header("Origin", "https://www.densuke.biz")
    .header("Referer", "https://www.densuke.biz/confirm")
    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
    .build();

// redirect follow を無効にして 302 Location から cd を取り出す
HttpResponse<Void> response = client.send(request,
    HttpResponse.BodyHandlers.discarding());

String location = response.headers().firstValue("Location").orElseThrow();
// location = "complete?cd=wAeAQgkBpAAgeMrK&sd=inNwg4mqS2.wk"

// cd の抽出
Matcher m = Pattern.compile("cd=([A-Za-z0-9]+)").matcher(location);
if (!m.find()) throw new RuntimeException("cd を取得できませんでした");
String cd = m.group(1);
String densukeUrl = "https://densuke.biz/list?cd=" + cd;
```

---

## 7. 未検証事項（将来追加調査）

- **削除 API**: sd を使ってテストイベントを削除する方法（`/delete?cd=xxx&sd=xxx` のような可能性）
- **編集 API**: 作成後に日程追加・変更する方法（本機能では「作成時に全日程確定」で運用するので不要）
- **パスワード保護あり**の場合の挙動（本機能では pw=0 固定で問題なし）
- **eventchoice=2, 3 の場合**のスクレイピング時の違い（既存 scraper は `○/△/×` 判定なので、eventchoice=1 で揃える必要あり）

---

## 8. 設計への反映事項

1. **eventchoice は必ず `1`（○△×）で固定** — 既存 scraper のロジック（`col3`=○, `col2`+△ 判定）と整合
2. **pw は `0`（パスワードなし）で固定** — 運用上パスワード不要
3. **日程文字列は `{M}/{D}({曜}) {HH:MM}～{会場} {N}試合目` 厳守** — scraper のパターンに依存
4. **Java からの送信は `URLEncoder.encode(value, UTF_8)` で問題なし** — curl の bug は Java には影響しない
5. **302 の Location ヘッダーから cd を取得** — `HttpClient` は `redirect(NEVER)` 相当で初期化
6. **`densuke_urls` テーブルには `cd` と `sd` 両方保存を検討** — sd は将来の編集機能で必要になる可能性

---

## 9. テストイベントの扱い

今回作成したテストイベント `cd=wAeAQgkBpAAgeMrK` は、伝助サーバー上に残っている。削除方法が判明したら削除すること。URL: https://densuke.biz/list?cd=wAeAQgkBpAAgeMrK
