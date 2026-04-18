# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ドキュメント更新ルール

**実装が完了したら、以下のドキュメントを必ず最新の状態に更新すること。**

| ファイル | 内容 |
|---------|------|
| `docs/SPECIFICATION.md` | 仕様書 |
| `docs/SCREEN_LIST.md` | 画面一覧 |
| `docs/DESIGN.md` | 設計書 |

- 新機能追加・既存機能の変更・画面の追加や変更があった場合、該当するドキュメントに漏れなく反映する
- ドキュメントの更新は実装コードと同じコミットに含める

## プロジェクト概要

競技かるたの対戦記録管理アプリ（Match Tracker）。Java Spring Boot バックエンド + React フロントエンドのモノレポ構成。

## 開発コマンド

### バックエンド（`karuta-tracker/`）
```bash
# 起動（PostgreSQL が必要）
cd karuta-tracker && ./gradlew bootRun

# ビルド
./gradlew build

# テスト実行
./gradlew test

# 単一テストクラス実行
./gradlew test --tests "com.karuta.matchtracker.service.MatchServiceTest"

# 単一テストメソッド実行
./gradlew test --tests "com.karuta.matchtracker.service.MatchServiceTest.testCreateMatch"
```

### フロントエンド（`karuta-tracker-ui/`）
```bash
cd karuta-tracker-ui && npm install   # 初回のみ
npm run dev      # 開発サーバー起動
npm run build    # プロダクションビルド
npm run lint     # ESLint
```

### Docker（開発環境）
```bash
docker-compose -f docker-compose-dev.yml up   # PostgreSQL + アプリ起動
```

## アーキテクチャ

### ディレクトリ構成
- `karuta-tracker/` - Spring Boot 3.4.1 / Java 21 / Gradle バックエンド
- `karuta-tracker-ui/` - React 19 / Vite / Tailwind CSS フロントエンド
- `database/` - SQLスキーマ・マイグレーション

### バックエンド構成（`karuta-tracker/src/main/java/com/karuta/matchtracker/`）
- `controller/` → `service/` → `repository/` → `entity/` の標準的なレイヤードアーキテクチャ
- `dto/` - リクエスト/レスポンス用DTO（Entity ↔ DTO変換は `fromEntity()` 静的メソッド）
- `interceptor/` - リクエストインターセプター（認証チェック等）
- `annotation/` - カスタムアノテーション（`@RequireRole`によるロール制御）
- `scheduler/` - スケジュールタスク

### フロントエンド構成（`karuta-tracker-ui/src/`）
- `api/` - Axiosベースの各APIクライアント（`client.js`で共通設定）
- `pages/` - 機能別ページコンポーネント（matches, players, practice, pairings, lottery, notifications）
- `components/` - 共通コンポーネント
- `context/` - React Context（AuthContext で認証管理）

### データベース
- ローカル・本番ともにRenderのPostgreSQLに接続（`dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com:5432/karuta_tracker_b297`）
- バックエンド起動時に以下の環境変数の設定が必須:
  - `DB_URL=jdbc:postgresql://dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com:5432/karuta_tracker_b297`
  - `DB_USERNAME=karuta`
  - `DB_PASSWORD=9wvobIcnZknsLP5owc9bQDKOWHmiekNE`
- 環境変数なしで起動すると `localhost:5432` に接続しようとして失敗する
- MySQL 8.0 は CI でのみ使用
- 論理削除パターン: `deleted_at` カラムで管理

### 認証
- ロールベース: SUPER_ADMIN / ADMIN / PLAYER
- localStorage + ダミートークン方式（プロトタイプ段階）
- `@RequireRole` アノテーションでエンドポイントごとにアクセス制御

### デプロイ
- Render.com（`render.yaml`）、ヘルスチェック: `/actuator/health`
- CI: GitHub Actions（`.github/workflows/test.yml`）- JUnit + Jacoco（最低カバレッジ 60%）

---

## 最重要ルール：実装・編集前の認識合わせ

**いかなる実装・ファイル編集も、不明点やあやふやな点が1つでもあれば、必ず先にユーザーと認識合わせを行うこと。ユーザーの目的をくみ取り、その実現に際し不明事項があれば必ず確認する**

### 基本方針
1. ユーザーの指示を受けたら、ユーザーの目的をくみ取り、その実現に際し足りない情報や曖昧な点を洗い出す
2. 少しでも不確かな点があれば、実装やファイル編集に着手せず質問する
3. 認識合わせの中で新たな不明点が出たら、それも解決してから進める
4. すべての不明点が解消されて初めて実装に移る

### ユーザーの指示が一般的なベストプラクティスと異なる場合
ユーザーの考えが間違っている可能性もある。以下に該当する場合は、必ず「本当にそれでよいか」を確認すること：
- 一般的なベストプラクティス・ゴールデンスタンダードに反する指示
- セキュリティやパフォーマンスのリスクがある方針
- 既存のコードベースの設計方針と矛盾する指示

確認の際は、なぜ懸念があるのかを具体的に説明し、代替案があれば提示する。ただし最終判断はユーザーに委ねる。

### 禁止事項
- 不明点があるのに推測・仮定で実装を進める
- 「おそらくこうだろう」で勝手に判断する
- 複数の実装方法がある場合に、確認なしで1つを選ぶ
- ユーザーの意図を確認せずに「改善」を加える

---

## 影響範囲の調査義務

**実装・修正を行う前に、必ず既存機能への影響範囲を調査すること。**

変更によるデグレード（既存機能の意図しない破壊）を防ぐため、コードに手を加える前に以下の調査を必ず実施する。

### 調査手順

1. **変更対象の特定**
   - 変更するファイル、クラス、メソッド、コンポーネントを明確にする

2. **依存関係の洗い出し**
   - 変更対象を呼び出している箇所（上流）を検索する
   - 変更対象が呼び出している箇所（下流）を確認する
   - フロントエンド ⇔ バックエンド間の連携（APIエンドポイント、リクエスト/レスポンス形式）への影響を確認する

3. **影響の評価**
   - 変更によって既存の動作が壊れる可能性がある箇所をリストアップする
   - DB スキーマの変更が必要かを確認する
   - 共通コンポーネントやユーティリティへの影響を確認する

4. **調査結果の共有**
   - 影響範囲が広い場合や、既存機能に副作用が生じる可能性がある場合は、実装着手前にユーザーに報告し確認を取る

### 禁止事項
- 影響範囲を調査せずにコードを変更する
- 変更対象のファイルだけを見て、呼び出し元・呼び出し先を確認しない
- 「このファイルだけの変更だから大丈夫だろう」と思い込む

---


