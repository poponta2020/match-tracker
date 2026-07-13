---
name: ship-pr1033-reshuffle-except-locked
description: PR#1033（/pairings ロック以外を再シャッフル・auto-match lockedPairs 契約拡張）の出荷記録
metadata:
  type: project
---

## 概要
`/pairings`（`PairingGenerator`）で、ロックした組を保持したまま**ロック以外の組だけを既存アルゴリズムで再シャッフル**する導線を追加。バックエンドの `autoMatch` を後方互換で拡張し、フロントに動的文言・確認ダイアログ付きの再シャッフルボタンを常設。

## 設計の核（後方互換の肝）
- `AutoMatchingRequest.lockedPairs`（nullable, nested `LockedPairInput{player1Id, player2Id}`）を追加。
  - `null`（既存の新規作成フロー＝「対戦編集」）: 従来どおり DB の `hasResult`/`locked` から保持組を導出（挙動不変）。
  - 非null（**空配列 `[]` を含む**・再シャッフル）: 手動ロックはクライアント指定を正・DB `locked` は無視、`hasResult` は常に DB 保護、未保存ロック組も保持（ただし両選手が当日アクティブ参加者=DB の場合のみ。任意ID・別団体名のエコーを防ぐ）。
- フロント: `buildAutoMatchBody` で `lockedPairs === undefined`（対戦編集＝キー省略）と配列（再シャッフル＝空配列でも必ず送信）を厳密に分岐。この差が「ローカルで解除した組が再シャッフルされる」核心。

## auto-review-loop（3ラウンド収束）
- **R1 (Codex, high)**: needs_changes → blocker=未保存lockedPairsの非参加者IDが `lockedPairings` にエコー（参加者の真=DB違反・別団体名漏れ）／should_fix=`lockedPairs:[null]` で500。両方修正（`activeParticipantIdSet` 限定＋`lp!=null` ガード＋防御テスト2件）。
- **R2 (high)**: pass。nit=再シャッフル後ドラフトの `isEditingExisting` が常にfalse（既存編集→タブ往復で全削除ボタン消失）→ `runAutoMatch(editingExisting)` 引数化で修正。
- **R3 (high)**: pass、指摘ゼロ。累計 Codex 約250k/500k。
- **AC適合(acceptance-reviewer)**: pass（AC-1〜9・R1〜R5 全 satisfied、Non-goals逸脱なし）。
- **追加 /code-review high（差分>400行）**: correctness/regression/contract の3角度すべて指摘なし([])。非ブロッキング注記のみ（未保存ロックのdedupがpair-key単位＝`[{A,B},{A,C}]`API直送でAが二重ブッキングされ得るが実UIでは到達不能・read-only・Non-goal抵触のため未修正）。

## verify（本番Render DB・read-only）
2026-07-19 すずらん(9名)で generate→lock(未保存)→reshuffle→cancel を実UI駆動。`autoMatch` は `@Transactional(readOnly=true)` で本番に一切書かない（確定して保存=createBatchのみ書込）ことを利用し、検証後 pairings 0件のまま=無変更を確認。lockedPairs 送信ボディ（未保存ロック `[{29,73}]`／全解除時 `[]`／2組ロック `[{29,73},{39,19}]`）をXHR捕捉で実証。

## DoDゲート
初回は環境要因で A1/A2（main の bootRun がgradleロック）・C1（codex結果がworktree側）・D1（memory記録がharness側）が FAIL。worktree からゲート実行＋本記録で解消。B1 CI は green（test 4m45s、head 72080fa2 で conclusion=success）。

## Issue・PR
- 親Issue #1029、子Issue #1030(BE)/#1031(FE)/#1032(docs)。PR本文の `Closes` で main マージ時に自動クローズ。
- PR #1033: https://github.com/poponta2020/match-tracker/pull/1033
- 成果物: `docs/features/pairing-reshuffle-except-locked/`（requirements.md / implementation-plan.md）。

## 教訓
- **advisor の前提が事実不完全だった good例**: advisor は「client lockedPairs は除外に使うだけでデータ注入しない」と判断したが、実際は未保存組を `lockedPairings`（レスポンス）にエコーする経路があり Codex R1 が捕捉。advisor 指摘と実装の実挙動が食い違う時は実挙動を優先して再確認する。
- **memory の二系統**: devflow gate/ship は**リポジトリ追跡の** `.claude/memory/` を見る（harness の `~/.claude/projects/.../memory/` とは別）。PR記録は repo 側にも置くこと。
- verify で本番を触るなら read-only エンドポイント（`@Transactional(readOnly=true)`）かを先に確認すると安全に実駆動できる。
