---
name: history
description: 今日の作業内容をhistory/YYYY-MM-DD.mdにまとめて保存するスキル。1日の作業の締めに使用する。
disable-model-invocation: true
user-invocable: true
allowed-tools: Read, Write, Bash
---

# /history - 作業記録の作成

今日の作業内容を `history/YYYY-MM-DD.md` にまとめて保存してください。

## 手順

1. 今日の日付（`currentDate` から取得）で `history/YYYY-MM-DD.md` を作成
2. この会話で行った作業を以下の形式でまとめる：

```markdown
# 作業記録 YYYY-MM-DD

## 概要
（1〜2行で今日の作業のまとめ）

---

## 完了した作業

### 1. （作業タイトル）
- **問題**: （何が問題だったか）
- **修正**: （何をしたか）
- **ファイル**: （変更したファイル）

（以降、作業ごとに繰り返し）

---

## 変更ファイル一覧

| ファイル | 変更内容 |
|---------|---------|
| `ファイルパス` | 変更内容 |

---

## 現在のブランチ状況

- ローカル: （ブランチ名）
- 本番: （状況）

## 次のステップ

- （残タスクや次にやること）
```

3. 既存ファイルがある場合は上書きせず、末尾に追記する
4. 完了したら「`history/YYYY-MM-DD.md` を作成しました」と報告する
