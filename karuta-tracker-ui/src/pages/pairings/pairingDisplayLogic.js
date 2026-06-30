// 組み合わせ作成画面（PairingGenerator）の「新規作成UI」表示可否を判定する純粋関数。
// 本番 JSX とテスト（PairingGenerator.integration.test.jsx）が同じ関数を import することで、
// 表示条件の退行 ―― 例: 過去の `!hasUnlockedPairings`（全組ロック時に作成UIが復活）への
// 揺り戻し ―― を確実に検知できるようにする。

/**
 * 参加者一覧セクションの表示可否。
 * 既存の組み合わせが1件でもあれば（結果入力済み / 手動ロック / 未ロックのいずれでも）作成UIは出さず、
 * 組み合わせ表示（閲覧モード / 編集モード）に統一する。これにより結果未入力の試合と表示を一貫させる。
 *
 * @param {Array} pairings 現在表示中の組み合わせ配列
 * @returns {boolean}
 */
export const shouldShowParticipantSection = (pairings) => pairings.length === 0;

/**
 * 「自動組み合わせ」ボタンの表示可否。
 * 組み合わせ未作成 かつ 参加者あり かつ 閲覧専用でない（他試合に未保存変更がない）かつ 日付選択済み。
 *
 * @param {object} params
 * @param {boolean} params.isReadOnly 他試合に未保存変更がある等で閲覧専用か
 * @param {string} params.sessionDate 選択中の練習日（未選択は空文字）
 * @param {Array} params.participants 当該試合の参加者配列
 * @param {Array} params.pairings 現在表示中の組み合わせ配列
 * @returns {boolean}
 */
export const shouldShowAutoMatchButton = ({ isReadOnly, sessionDate, participants, pairings }) =>
  !isReadOnly && !!sessionDate && participants.length > 0 && pairings.length === 0;

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
 * 閲覧→編集モードへ切り替える際に、キャンセルスロットを「空き」として実体化した
 * 新しい pairings 配列を返す（イミュータブル：新配列・新オブジェクト）。
 *
 * - 両方キャンセルの組は除去（filter で落とす）。「空き vs 空き」の無意味な行を残さない。
 * - 片方キャンセルの組は、その選手の playerXId / playerXName を null にし、
 *   playerXCancelled を false に戻す（＝編集モードの既存ロジックで「空き」スロットになる）。
 * - それ以外（キャンセルなし）の組はそのまま（同一参照ではなく内容は不変）。
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
    .filter((p) => !isBothCancelled(p))
    .map((p) => {
      if (!hasAnyCancelled(p)) return { ...p };
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
      return next;
    });
