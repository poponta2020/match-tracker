---
name: process-lean-review-pipeline-v080
description: devflow v0.8.0〜v0.9.0 でレビューパイプラインを軽量化（AC適合・追加code-review・/verify・マージ前CI待ちを標準ループから除外、pass即終了）。PR#1033 の実測に基づく判断と再装着条件の記録
metadata:
  type: project
---

## 判断（2026-07-14）

PR#1033 の実測（全114分、内訳: 実装25分／スイート7分／verify 22分／Codexループ19分／AC適合13分／追加code-review 12分／ship+DoD 11分）で、**AC適合と追加/code-reviewは検出ゼロ、Codexの確認専用ラウンド(R3)も指摘ゼロ**だったことを受け、ユーザーが「95%品質・短時間」の方針でレビュー工程の削減を決定。devflow v0.8.0（claude-devflow PR#2、merge 0caeab0）として実装。

## 変更内容

1. **auto-review-loop 収束条件**: pass が出たら即終了。nit が残っていても /fix で修正のみ行い、確認専用の再レビューラウンドは追加しない
2. **AC適合チェック（acceptance-reviewer）**: 標準ループから削除。エージェント定義は手動起動用に存続
3. **大型・高リスク差分の追加 /code-review**: 削除（旧 3-d.6、DIFF>400行 or high 判定で発動していた）
4. **/implement 完了後の /verify 実動作検証**: 削除（PR作成へ直行）。prepare-pr の `Verified: live` 行も廃止
5. **マージ前の CI 完了待ち（v0.9.0、claude-devflow PR#3、merge db23cf0）**: auto-review-loop の `gh pr checks --watch` を待ちなし1回判定に変更（失敗確定時のみ ship 中断、pending はそのままマージ）。gate-dod B1 も pending を PASS 扱いに。マージ後に CI が赤になったら /quickfix・/bug-report で追修正する運用
6. 受け側: CLAUDE.md 開発フロー行・project-profile.md の低リスクパス説明を追随更新

## 再装着の条件（ユーザー合意）

「支障が出るようなら元に戻します」— 出荷後に以下が起きたら該当工程の復帰を検討する:
- 要件と違うものが出荷される（→ AC適合を戻す）
- Codex が見逃したバグが本番に出る（→ 追加 /code-review を高リスクパス限定で戻す）
- 実動作しない UI が出荷される（→ /verify を戻す）
- マージ後の CI 赤が頻発して追修正コストが待ち時間を上回る（→ CI 待ちを戻す）

再装着ポイントは devflow の各 SKILL.md に「v0.8.0 で外した工程」注記として明記済み。

## 関連

- 出荷実測の元データ: [ship_pr1033_reshuffle_except_locked.md]
- DoD ゲートの誤検知（別問題・未修正）: gate-dod と profile 形式の版不整合は installed_plugins.json が cache 0.4.0 を指し続けていたのが根本原因。0.8.0 配置時に registration も修正済み
