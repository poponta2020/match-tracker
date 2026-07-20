---
name: impl_pairing_always_visible_edit_button
description: /pairings のLINE送信用テキスト導線を常時表示し、主アクションを「対戦編集」1つに統合して右上へ常設（2026-07-19、PR #1113）
metadata:
  type: project
category: ship
tags: [quickfix, pairing-always-visible-edit-button]
---

# /pairings のタブ位置ブレ解消＋主アクション統合（PR #1113）

**PR**: [#1113](https://github.com/poponta2020/match-tracker/pull/1113) — `fix/pairing-always-visible-edit-button`
**マージ**: 成功（CI `test` は pending のままマージ＝v0.9.0 方針）
**Issue**: なし（/quickfix 直接依頼）

## 症状と根本原因

**①** LINE送信用テキスト導線が `resolveLineTextTarget` の返り値 null（＝その試合も全試合も未完成）のときセクションごと `return null` していた。この導線は試合番号タブの**上**にあるため、組み合わせ済みの試合と未作成の試合をタブで行き来するたびにタブが上下していた。

**②** 主アクションが3系統に分裂（参加者ヘッダの「更新」「追加」／右下の「対戦編集」／組み合わせヘッダの「編集」）。表示条件が相互排他なため位置が揃わなかった。

## 修正

- LINE導線のトグル見出しを**常時描画**。生成可否判定（`computeLineTextAvailability` / `resolveLineTextTarget`）は不変
- 主アクションを、パネル内で**常に描画される唯一の行**「N試合目」の右端に常設した「対戦編集」1つへ統合
- 表示・挙動の判定は全て `pairingDisplayLogic.js` の純関数に集約（`shouldShowParticipantSection` / `resolvePairingEditAction` / `isPairingEditDisabled` / `shouldShowEditingArea`）

## 設計上の肝（再発しやすい点）

1. **既存組があるとき「対戦編集」で auto-match を呼んではいけない**。ラベル統合の勢いで `handleAutoMatch` に一本化すると、保存済みの組み合わせが黙って組み直されて失われる。`resolvePairingEditAction` の `'edit-existing'` は編集モード切替のみ。**ここが本修正で唯一データを壊しうる箇所**で、実機のネットワークログで auto-match 未発火を確認済み
2. **「常に同じ位置」を満たせる行はパネル内に1つしかない**。参加者ヘッダ（組0件のみ）と組み合わせヘッダ（組1件以上）は相互排他なので、どちらに置いても組の有無で位置が動く（①と同じ種類のバグ）
3. **無効時の代替要素は「同じ骨格・同じラベル」にする**。生成リンクを文言の違う `<p>` に差し替えたら、トグルを開いたままタブ移動で 6px ずれた（advisor が指摘・実測で確認）。狭い画面では折り返してさらにずれる。同じ class の `<button disabled>` にして delta 0px を desktop 1280 / mobile 375 で確認
4. **参加者0名でも編集モードへ入る（`'empty-edit'`）のはユーザーが選択した仕様**。この画面から「追加」を消したので、待機中セクションの「選手追加」への到達経路を残す必要がある。空編集モードは**ドラフト（`saveDraft`）を必ず作る** — 選手追加が `currentSession` を更新し、ドラフトが無いと復元 useEffect で編集モードごと巻き戻る（#485 と同型）
5. `isEditing` は `pairings.length > 0` と独立した明示フラグ。**全ての離脱経路でリセットが要る**（日付変更・タブ切替・保存・リセット・削除・キャンセル・`loadExistingPairingsToState`）

## 検証

- `vitest run --no-file-parallelism` **747 green**（4回中3回 green。1回だけ2件失敗したが再現せず＝既知のスワイプ/カルーセル系フレーク。[[reference_frontend_swipe_tests_flaky_parallel]]）
- `npm run lint` 0 error
- 実機（本番DB・2026-07-18 のセッション）: 組あり(試合1)/組なし(試合2)でタブ位置・LINE見出し・ボタン座標が完全一致（トグル開閉どちらでも delta 0px、desktop/mobile 両方）

## auto-review

**1R pass**（effort=high・src差分646行>400でルーブリック判定、36,832 tokens、blockers/should_fix/nits すべて0）。
プロンプトに「検証済み事項5点」を明記して偽陽性を予防した（[[auto_review_round_pr1083]] の教訓の適用）。codex は中立 cwd から stdin 経由で diff を渡す（[[auto_review_round_pr1102]]）。

## 検証環境の罠（次回の時短）

- **preview_start の `cwd` は絶対パス不可・プロジェクトルート配下の相対パスのみ**。worktree の画面を Browser で見るには、プロジェクトルートに worktree UI へのジャンクションを張って相対 cwd で指すか、Bash で vite を別ポート起動して `preview_start {url}` で開く（今回は後者・5174 で成功。CORS 許可に 5174 が入っている）
- **`computer {action:"screenshot"}` が本環境で常時タイムアウトする**。レイアウトのブレ検証は `javascript_tool` で `getBoundingClientRect().top` を実測する方が確実（「何px動いたか」を数値で言える分むしろ優れる）
- ログインは `localStorage.currentPlayer` に `{id,name,role,kyuRank}` を注入すれば通る。**`kyuRank` が無いと PrivateRoute が `/profile/edit?setup=true` へリダイレクトする**
