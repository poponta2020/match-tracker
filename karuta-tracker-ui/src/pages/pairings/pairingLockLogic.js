// 手動ロックの明示保存化（pairing-lock-explicit-save-and-help）に関する純粋ロジック。
//
// PairingGenerator と回帰テストで同じ実装を共有し、本番の退行をテストで確実に捕捉する
// （pairingDisplayLogic / pairingDragLogic / pairingDraftLogic 等と同じ「純粋関数を切り出して
// 本番・テスト双方で import する」方針）。テスト側に条件式をコピーしないことで、本番側の
// ロジックが変わった場合にテストが確実に失敗する。

/**
 * 対象 index の組の locked を切り替えた新しい pairings 配列を返す（イミュータブル更新）。
 * サーバ通信は行わず、呼び出し側で state 更新・未保存ドラフト反映を行う。
 */
export const togglePairingLock = (pairings, index, locked) =>
  pairings.map((p, i) => (i === index ? { ...p, locked } : p));

/**
 * その組をロックできるか（両選手が揃っている組のみロック可能）。
 */
export const canLockPairing = (pairing) =>
  !!(pairing && pairing.player1Id && pairing.player2Id);

/**
 * 解除ボタンを表示するか（手動ロック専用: ロック中かつ結果未入力。閲覧/読み取り専用時は非表示）。
 * 結果入力済み組は保存リクエストに含めず解除（locked=false）を永続化できないため対象外とし、
 * 「ロック」バッジの表示条件（locked && !hasResult）とも一致させる（結果を消すには「リセット」を使う）。
 */
export const canShowUnlock = ({ isReadOnly, isViewMode, pairing }) =>
  !isReadOnly && !isViewMode && !!pairing.locked && !pairing.hasResult;

/**
 * 一括保存（createBatch）リクエストの各要素を生成する。
 * 結果入力済み（hasResult）はバックエンドで保護されるため送信対象から除外し、
 * 両選手が揃った完成済みの組のみを対象とする（未完成行はUIの保存ボタン無効化でも防ぐが、
 * ヘルパー自身でも不変条件として担保する）。手動ロック状態 locked を boolean で同梱して永続化する。
 */
export const buildSaveRequests = (pairings) =>
  pairings
    .filter((p) => !p.hasResult && p.player1Id && p.player2Id)
    .map((p) => ({ player1Id: p.player1Id, player2Id: p.player2Id, locked: !!p.locked }));

/**
 * 保存対象が無いか判定する。
 * 「完成した組（両選手あり・結果未入力。手動ロック含む）が0、かつ待機者0」のとき true。
 */
export const hasNothingToSave = (pairings, waitingPlayers) =>
  pairings.filter((p) => !p.hasResult && p.player1Id && p.player2Id).length === 0
  && waitingPlayers.length === 0;

/**
 * 「確定して保存」ボタンを未完成組ゆえに無効化すべきか。
 * 片方だけ埋まった作りかけの組があれば保存させない既存ガード（結果入力済み・手動ロック組は対象外）。
 * ただし対戦相手キャンセル由来で空き化された組（cancelledEmptied）は対象外とする。
 * キャンセルで空いた組は仕様上そのまま保存でき、buildSaveRequests で未完成として送信されず、
 * 生存側の選手はアクティブ参加者として残る（pairing-cancelled-opponent）。
 */
export const hasBlockingIncompletePair = (pairings) =>
  pairings.some(
    (p) => !p.hasResult && !p.locked && !p.cancelledEmptied && (!p.player1Id || !p.player2Id)
  );
