# リッチメニュー設定手順

## 前提
- バックエンドがデプロイ済みであること
- リッチメニュー画像（2500x1686px、PNG or JPEG）が用意されていること
- SUPER_ADMINのユーザーIDを把握していること

## 手順

### 1. 画像を用意する

画像サイズは **2500x1686px** にリサイズする（LINE API の仕様で正確なサイズが必須）。

### 2. APIを実行する

```bash
curl -X POST "https://<APIドメイン>/api/admin/line/rich-menu/setup" \
  -H "X-User-Id: <SUPER_ADMINのユーザーID>" \
  -H "X-User-Role: SUPER_ADMIN" \
  -F "image=@richmenu.png"
```

**例（ローカル環境）:**
```bash
curl -X POST "http://localhost:8080/api/admin/line/rich-menu/setup" \
  -H "X-User-Id: 1" \
  -H "X-User-Role: SUPER_ADMIN" \
  -F "image=@richmenu.png"
```

### 3. レスポンスを確認する

```json
{
  "successCount": 48,
  "failureCount": 2,
  "failures": [
    "チャネル名A (作成失敗)",
    "チャネル名B (画像アップロード失敗)"
  ]
}
```

- `successCount`: 設定成功したチャネル数
- `failureCount`: 失敗したチャネル数
- `failures`: 失敗したチャネル名と理由

### 4. 失敗があった場合

同じAPIを再実行すれば、全チャネルに再設定されます（既存のリッチメニューは上書きされます）。

### 5. 動作確認

LINEアプリでリンク済みのトーク画面を開き、以下を確認:
- リッチメニューが表示される
- 各ボタンをタップして正しい応答が返る
