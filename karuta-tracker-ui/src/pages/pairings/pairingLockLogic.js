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
  // キャンセル由来の空き組（cancelledEmptied）は「削除して保存する対象」なので保存対象なしにしない。
  // これがあると handleSave が空 requests で createBatch を呼び、孤立した既存組を削除できる
  // （「生存側 vs 空き」だけが残る試合でも空きのまま保存して組レコードを消せる。pairing-cancelled-opponent）。
  && !pairings.some((p) => p.cancelledEmptied)
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

/**
 * 「ロック以外を再シャッフル」で auto-match に送る lockedPairs 入力を現在の画面状態から算出する。
 * ロック済みの組（結果入力済み hasResult または手動ロック locked。未保存ロックも含む）のうち、
 * 両選手が揃った組だけを {player1Id, player2Id} に射影する（null player id を送らないガード。
 * buildSaveRequests と同じく「両選手あり」を不変条件にする）。
 *
 * バックエンドは結果入力済みを常に DB から保護するため、hasResult 組を含めても害はなく、
 * 手動ロック（locked）の真をクライアントから渡すことがこの関数の主目的。
 */
export const computeLockedPairsInput = (pairings) =>
  (pairings || [])
    .filter((p) => p && (p.hasResult || p.locked) && p.player1Id && p.player2Id)
    .map((p) => ({ player1Id: p.player1Id, player2Id: p.player2Id }));

/**
 * auto-match（自動組み合わせ／再シャッフル）へ送る request body を組み立てる。
 * lockedPairs の undefined/配列を「クライアント権威か否か」の境界として厳密に扱う:
 *  - lockedPairs === undefined（新規作成＝「対戦編集」）: body に lockedPairs キーを含めない
 *    → バックエンドは従来どおり DB の hasResult/locked から保持組を導出（後方互換・挙動不変）。
 *  - lockedPairs が配列（空配列 [] を含む・再シャッフル）: 常に body に lockedPairs を入れる
 *    → バックエンドは手動ロックをクライアント指定で判定（DB の locked は無視・結果入力済みは常に保護）。
 * [] は truthy なので `if (lockedPairs)` では区別できない。undefined 判定を唯一の分岐条件にする。
 */
export const buildAutoMatchBody = (sessionDate, matchNumber, lockedPairs) =>
  lockedPairs === undefined
    ? { sessionDate, matchNumber }
    : { sessionDate, matchNumber, lockedPairs };
