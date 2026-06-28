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
