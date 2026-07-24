---
name: ship-bottom-nav-capsule-home-teleport
description: ボトムナビ カプセルのホーム経由瞬間移動バグ修正
type: project
---

ボトムナビのカプセルが「ホーム経由の遷移でのみ瞬間移動する」バグを修正し出荷(2026-07-24、**PR #1167 マージ済** merge=55e234cd、Closes #1166)。フォローアップ元は[[impl_bottom_nav_liquid_glass]]。

**根本原因**: `/`(ホーム)だけが`<AuthRoute>`でラップされ、他4ナビ先は`<ProtectedPage>`を`<Routes>`outletに直接置いていた。Reactはoutlet位置の要素型でfiberを調停するため、`/`出入りで`AuthRoute`↔`ProtectedPage`の型不一致→共有`<Layout>`(カプセルの`<span>`)をアンマウント/リマウント→`transition:transform 427ms`が効かず瞬間移動。他→他は同型`<ProtectedPage>`でLayout fiber再利用→グライド(ユーザーの「他→他は正常」報告が再利用を実証)。

**修正(Option A・最小)**: 5ナビ先を同型`<ProtectedPage>`に揃えLayout fiberを再利用。`PrivateRoute`に任意prop`publicFallback`追加(未認証時: あれば描画/無ければ従来通り/loginリダイレクト=後方互換、`publicFallback!==undefined`でnull明示許容と未指定を区別)。`/`を`<ProtectedPage publicFallback={<Landing/>}>`に変更、`<AuthRoute>`撤去、未使用の`AuthRoute.jsx`削除。**認可の緩和ではない**(未認証時の表示先差し替えのみ、認証済み判定・プロフィール未設定リダイレクトは不変)。

**罠**: (1)git add複数pathspecでgit rm済みパスを含めると全abort→残りが未ステージのまま空commit(amendで回収)。(2)main workdirの未追跡docs/bugs/1166がbranch追跡ファイルとff-merge衝突→事前rm必須([[reference_ship_ffmerge_untracked_feature_docs]]同型)。

**変更ファイル**: PrivateRoute.jsx(publicFallback)、App.jsx(/ルート+import)、AuthRoute.jsx(削除)、PrivateRoute.test.jsx(新規AC-3)、SCREEN_LIST.md(AuthRoute記載3箇所除去)。全856テストgreen・lint0err・build成功。

**回帰テストの範囲**: AC-3(PrivateRoute挙動)はauto-test。**AC-1(カプセルのグライド)はverify=実機/プレビュー目視が残タスク**(手製MemoryRouterレプリカは/が再AuthRouteラップでもpassし真の回帰を守れないため意図的にauto-test化せず、advisor助言)。auto-reviewは[[auto_review_round_pr1167]]。
