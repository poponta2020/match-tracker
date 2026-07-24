// 組み合わせ作成画面（PairingGenerator）の「新規作成UI」表示可否を判定する純粋関数。
// 本番 JSX とテスト（PairingGenerator.integration.test.jsx）が同じ関数を import することで、
// 表示条件の退行 ―― 例: 過去の `!hasUnlockedPairings`（全組ロック時に作成UIが復活）への
// 揺り戻し ―― を確実に検知できるようにする。

/**
 * 参加者一覧セクションの表示可否。
 * 既存の組み合わせが1件でもあれば（結果入力済み / 手動ロック / 未ロックのいずれでも）作成UIは出さず、
 * 組み合わせ表示（閲覧モード / 編集モード）に統一する。これにより結果未入力の試合と表示を一貫させる。
 * 編集モード中（isEditing）も非表示にする ―― 組0件のまま編集モードへ入った場合、
 * 同じ選手を待機中セクションが扱うため二重表示になるため。
 *
 * @param {object} params
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @param {boolean} params.isEditing 編集モードに入っているか
 * @returns {boolean}
 */
export const shouldShowParticipantSection = ({ pairings, isEditing }) =>
  (pairings || []).length === 0 && !isEditing;

/**
 * 常設の「対戦編集」ボタンが押されたときの動作を解決する。
 * ボタンの位置（パネル右上）・ラベル・見た目は画面状態によらず不変で、挙動だけが状態で変わる。
 *
 * - `'edit-existing'`: 既存の組がある → 閲覧モードから編集モードへ切り替えるだけ。
 *   **ここで自動組み合わせ（auto-match）を絶対に走らせてはならない**。走らせると保存済みの
 *   組み合わせが黙って組み直されて失われる（旧「編集」ボタンの挙動を維持する）。
 * - `'auto-match'`: 組が0件で参加者がいる → 従来どおり自動組み合わせを生成して編集モードへ。
 * - `'empty-edit'`: 組も参加者も0件 → 空の編集モードへ入る。この画面から参加者一覧の
 *   「追加」ボタンを廃止したため、待機中セクションの「選手追加」に到達できる経路を残す。
 *
 * @param {object} params
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @param {Array} params.participants 当該試合の参加者配列
 * @returns {'edit-existing'|'auto-match'|'empty-edit'}
 */
export const resolvePairingEditAction = ({ pairings, participants }) => {
  if ((pairings || []).length > 0) return 'edit-existing';
  if ((participants || []).length > 0) return 'auto-match';
  return 'empty-edit';
};

/**
 * 常設の「対戦編集」ボタンを無効化すべきか。
 * ボタンは常時レンダリングして高さを固定する（表示/非表示の切替でレイアウトが上下しないように）ので、
 * 押せない状態は非表示ではなく disabled で表現する。
 *
 * - `isReadOnly`: 他試合に未保存の変更があり、この試合は閲覧専用
 * - 既に編集モードに入っている（`isEditing` かつ閲覧モードでない）: これ以上「編集へ入る」操作はない
 *
 * @param {object} params
 * @param {boolean} params.isReadOnly 他試合に未保存変更がある等で閲覧専用か
 * @param {boolean} params.isEditing 編集モードに入っているか
 * @param {boolean} params.isViewMode 既存組み合わせの閲覧モードか
 * @returns {boolean}
 */
export const isPairingEditDisabled = ({ isReadOnly, isEditing, isViewMode }) =>
  !!isReadOnly || (!!isEditing && !isViewMode);

/**
 * 組み合わせ編集エリア（組一覧・待機中セクション・確定して保存）を描画するか。
 * 組が1件以上あるとき、または参加者0名の状態から空の編集モードへ入ったときに描画する。
 * 後者が無いと、参加者0名の試合で「対戦編集」を押しても待機中セクションの「選手追加」に
 * 到達できず、この画面から選手を登録する手段が消える。
 *
 * @param {object} params
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @param {boolean} params.isEditing 編集モードに入っているか
 * @returns {boolean}
 */
export const shouldShowEditingArea = ({ pairings, isEditing }) =>
  (pairings || []).length > 0 || !!isEditing;

/**
 * 「再シャッフル／ロックされた組以外をシャッフル」ボタンの表示可否。
 * 組み合わせが1件以上あり（pairings.length>0）、編集可能状態（閲覧専用でない・閲覧モードでない）のときに表示する。
 * 新規作成用「対戦編集」ボタン（pairings.length===0 時）とは排他になる。
 *
 * @param {object} params
 * @param {boolean} params.isReadOnly 他試合に未保存変更がある等で閲覧専用か
 * @param {boolean} params.isViewMode 既存組み合わせの閲覧モードか
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @returns {boolean}
 */
export const shouldShowReshuffleButton = ({ isReadOnly, isViewMode, pairings }) =>
  !isReadOnly && !isViewMode && pairings.length > 0;

/**
 * 閲覧時の「未組み合わせ選手（待機中）チップ一覧」セクションの表示可否。
 * 一部だけ組が作られている試合を閲覧しているとき、まだどの組にも入っていない参加者
 * （waitingPlayers）を読み取り専用チップで一覧表示するためのガード。
 *
 * 条件: (isReadOnly || isViewMode) && pairings.length > 0 && waitingPlayers.length > 0
 * - これは編集モードの待機中セクションのガード `!isReadOnly && !isViewMode` の**厳密な補集合**であり、
 *   閲覧用チップ一覧と編集用待機中セクションが同時に出ることはない（相互排他）。
 * - `pairings.length > 0` は必須。isReadOnly は「他試合を編集中に、まだ組が無いこの試合を見ている」
 *   状態（pairings.length === 0）でも真になりうるため、これが無いと参加者一覧セクション
 *   （shouldShowParticipantSection = pairings.length === 0 で表示）と二重表示になる。
 *
 * @param {object} params
 * @param {boolean} params.isReadOnly 他試合に未保存変更がある等で閲覧専用か
 * @param {boolean} params.isViewMode 既存組み合わせの閲覧モードか
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @param {Array} params.waitingPlayers 未組み合わせ（待機中）の参加者配列
 * @returns {boolean}
 */
export const shouldShowViewModeUnpairedSection = ({ isReadOnly, isViewMode, pairings, waitingPlayers }) =>
  (isReadOnly || isViewMode) && pairings.length > 0 && waitingPlayers.length > 0;

/**
 * 再シャッフルボタンの文言（ロック済みの組の有無で動的に切り替える）。
 * - ロック済みの組（hasResult または locked）が1件以上: 「ロックされた組以外をシャッフル」
 * - 0件: 「再シャッフル」
 *
 * @param {Array} pairings 現在表示中の組み合わせ配列
 * @returns {string}
 */
export const reshuffleButtonLabel = (pairings) =>
  (pairings || []).some((p) => p && (p.hasResult || p.locked))
    ? 'ロックされた組以外をシャッフル'
    : '再シャッフル';

// ─────────────────────────────────────────────────────────────────────────────
// 対戦相手キャンセル（pairing-cancelled-opponent）表示判定
//
// 取得API（getByDateAndMatchNumber 等）の各組DTOに付与される
// player1Cancelled / player2Cancelled（read-time・非破壊で算出されるboolean）を
// 閲覧/編集モードでどう扱うかの純粋関数。本番JSXとテストで同じ関数を import し、
// 条件式をテスト側にコピーしないことで退行を確実に検知する。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * その組の両選手がともにキャンセル済みか。
 * 両方キャンセルの組は試合として成立しないため、閲覧モードでは行ごと非表示・
 * 編集モードでは行ごと除去する。
 *
 * @param {object} pairing 組（player1Cancelled / player2Cancelled を含みうる）
 * @returns {boolean}
 */
export const isBothCancelled = (pairing) =>
  !!(pairing && pairing.player1Cancelled && pairing.player2Cancelled);

/**
 * その組のいずれか一方でもキャンセル済みか。
 * 閲覧モードでキャンセルタグ付きの行として描画するかの判定に使う。
 *
 * @param {object} pairing 組（player1Cancelled / player2Cancelled を含みうる）
 * @returns {boolean}
 */
export const hasAnyCancelled = (pairing) =>
  !!(pairing && (pairing.player1Cancelled || pairing.player2Cancelled));

/**
 * その行を「ロック/結果」表示として描画するか（行描画の優先順位）。
 * - 結果入力済み（hasResult）は常に最優先（結果が正。キャンセルは反映しない）。
 * - 手動ロック（locked）はキャンセルが無いときのみロック表示。片方キャンセルがある手動ロック組は
 *   ロック表示より「キャンセル表示（閲覧）/空き化（編集）」を優先する（ロックは崩れたとみなす）。
 *
 * @param {object} pairing 組
 * @returns {boolean}
 */
export const showsResultLockedRow = (pairing) =>
  !!(pairing && (pairing.hasResult || (pairing.locked && !hasAnyCancelled(pairing))));

/**
 * その行を閲覧モードで行ごと非表示にするか。
 * 両方キャンセルは試合として成立しないため非表示にするが、結果入力済み（hasResult）の組は
 * 結果が正なので残す（万一の不整合データでも記録を消さない）。
 *
 * @param {object} pairing 組
 * @returns {boolean}
 */
export const shouldHideRow = (pairing) =>
  isBothCancelled(pairing) && !(pairing && pairing.hasResult);

/**
 * 閲覧→編集モードへ切り替える際に、キャンセルスロットを「空き」として実体化した
 * 新しい pairings 配列を返す（イミュータブル：新配列・新オブジェクト）。
 *
 * - 両方キャンセルの組は除去（filter で落とす）。「空き vs 空き」の無意味な行を残さない。
 *   ただし結果入力済み（hasResult）は結果が正なので残す。
 * - 片方キャンセルの組は、その選手の playerXId / playerXName を null にし、
 *   playerXCancelled を false に戻す（＝編集モードの既存ロジックで「空き」スロットになる）。
 *   手動ロック組（locked）でもキャンセルで崩れたら locked を false にして編集可能にする。
 * - 結果入力済み（hasResult）の組はキャンセルを反映せずそのまま保持する。
 * - それ以外（キャンセルなし）の組はそのまま（内容は不変）。
 *
 * これにより編集モードの描画・ドラッグ・保存（buildSaveRequests は両選手揃った組のみ送信）は
 * 既存ロジックのまま動き、キャンセル者は空きになって保存時に未完成組として送信されず、
 * 生存側の選手はアクティブ参加者として残る（データ消失なし）。
 *
 * @param {Array} pairings 閲覧モードの組配列
 * @returns {Array} 編集モード用に実体化した新しい組配列
 */
export const materializeCancelledSlots = (pairings) =>
  (pairings || [])
    // 両方キャンセルの組は除去。ただし結果入力済み（hasResult）は結果が正なので残す。
    .filter((p) => !!p && (p.hasResult || !isBothCancelled(p)))
    .map((p) => {
      // 結果入力済みは結果が正。キャンセルを反映せず編集対象にもしない（そのまま保持）。
      if (!p || p.hasResult || !hasAnyCancelled(p)) return { ...p };
      const next = { ...p };
      if (next.player1Cancelled) {
        next.player1Id = null;
        next.player1Name = null;
        next.player1Cancelled = false;
      }
      if (next.player2Cancelled) {
        next.player2Id = null;
        next.player2Name = null;
        next.player2Cancelled = false;
      }
      // 手動ロック組でもキャンセルで組が崩れたら、空きにして編集可能にする（ロック解除）。
      next.locked = false;
      // キャンセル由来の空き組であることを記録（「確定して保存」ボタンの未完成ガードから除外し、
      // 空きのまま保存できるようにする。buildSaveRequests では未完成として送信されない）。
      next.cancelledEmptied = true;
      return next;
    });

// ─────────────────────────────────────────────────────────────────────────────
// キャンセル者アラート（pairing-cancel-alert-and-save-errors 変更1）判定
//
// 閲覧モードで表示中の試合に「作成済みの組へ残ったキャンセル済み参加者」がいるとき、
// 単一 OK ボタンのアラートで能動通知する。OK 押下で現在の試合を編集モード化
// （materializeCancelledSlots）してキャンセル者を「空き」にする。
// 本番 JSX とテストで同じ関数を import し、条件式をテストにコピーしない（退行検知）。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 閲覧モードで表示中の組配列から、キャンセル者アラートに列挙する選手名を集める。
 * - 結果入力済み（hasResult）の組は結果が正でキャンセルを反映しない（materializeCancelledSlots も
 *   そのまま保持する）ため対象外＝アラートに出さない。
 * - 片方キャンセルはその1名、両方キャンセルは両名を含む。
 * - map の順（＝画面の組順）で名前を返す。
 *
 * @param {Array} pairings 閲覧モードの組配列（player1Cancelled / player2Cancelled を含みうる）
 * @returns {string[]} キャンセルした選手名の配列（0件ならアラート不要）
 */
export const collectCancelledNames = (pairings) =>
  (pairings || []).flatMap((p) => {
    if (!p || p.hasResult) return [];
    const names = [];
    if (p.player1Cancelled && p.player1Name) names.push(p.player1Name);
    if (p.player2Cancelled && p.player2Name) names.push(p.player2Name);
    return names;
  });

/**
 * キャンセル者アラートを発火すべきか。
 * 閲覧モード（isViewMode）かつ編集ロックでない（!isReadOnly ＝他試合の未保存編集で読み取り専用でない）
 * かつ未確認（!acknowledged）で、結果未入力のキャンセル者が1名以上いるとき true。
 * acknowledged は「この試合で一度 OK 済み」を呼び出し側の Set 等で判定して渡す（同一マウント中の再発火防止）。
 *
 * @param {object} params
 * @param {boolean} params.isViewMode 既存組み合わせの閲覧モードか
 * @param {boolean} params.isReadOnly 他試合に未保存変更がある等で閲覧専用か
 * @param {boolean} params.acknowledged この試合で既にアラートを OK 済みか
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @returns {boolean}
 */
export const shouldTriggerCancelAlert = ({ isViewMode, isReadOnly, acknowledged, pairings }) =>
  !!isViewMode && !isReadOnly && !acknowledged && collectCancelledNames(pairings).length > 0;
