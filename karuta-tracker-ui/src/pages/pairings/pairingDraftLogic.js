/**
 * 未保存ドラフト（unsavedDraft）に関する純粋関数群。
 * React state を直接操作せず、新しいオブジェクトを返す。
 *
 * PairingGenerator.jsx と回帰テスト（#485）で同じ実装を共有することで、
 * 「テストが本番コードを検証しない」状態を避ける。
 */

/**
 * 選手追加後の同期処理。
 *
 * 待機リストへの追加に加え、同一 matchNumber の未保存ドラフトがあれば
 * ドラフトの waitingPlayers も同期する。これがないと、setCurrentSession 等で
 * useEffect が再実行された際にドラフトから復元されて新規選手が消える（#485）。
 *
 * @param {Object} params
 * @param {Array}  params.waitingPlayers
 * @param {Object} params.newPlayer  - { id, name }
 * @param {Object|null} params.currentDraft  - 現在の unsavedDraft.current
 * @param {number} params.matchNumber
 * @param {Array}  params.pairings
 * @param {boolean} params.isEditingExisting
 *
 * @returns {{ newWaiting: Array, newDraft: Object|null }}
 *          newDraft はドラフトを更新すべき場合のみ新オブジェクト、それ以外は currentDraft をそのまま返す
 */
export function syncDraftAfterAddingPlayer({
  waitingPlayers,
  newPlayer,
  currentDraft,
  matchNumber,
  pairings,
  isEditingExisting,
}) {
  const newWaiting = [...waitingPlayers, newPlayer];
  const draftMatches = !!currentDraft && currentDraft.matchNumber === matchNumber;
  const newDraft = draftMatches
    ? {
        matchNumber,
        pairings,
        waitingPlayers: newWaiting,
        isEditingExisting,
      }
    : currentDraft;
  return { newWaiting, newDraft };
}

/**
 * useEffect 内のドラフト復元ロジック。
 *
 * 同一 matchNumber のドラフトがあれば復元すべき state を返す。
 * なければ null。
 *
 * @param {Object|null} currentDraft  - unsavedDraft.current の現在値
 * @param {number} matchNumber
 *
 * @returns {{ pairings: Array, waitingPlayers: Array, isEditingExisting: boolean, isViewMode: boolean }|null}
 */
export function restoreDraftIfMatches(currentDraft, matchNumber) {
  if (!currentDraft || currentDraft.matchNumber !== matchNumber) return null;
  return {
    pairings: currentDraft.pairings,
    waitingPlayers: currentDraft.waitingPlayers,
    isEditingExisting: currentDraft.isEditingExisting,
    isViewMode: false,
  };
}
