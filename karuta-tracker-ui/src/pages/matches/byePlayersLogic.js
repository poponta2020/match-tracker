/**
 * 抜け番算出ロジック（純粋関数）
 *
 * バックエンドの `PracticeSessionService.enrichDtoWithMatchDetails` は
 * `DECLINED` / `WAITLIST_DECLINED` 以外の全ステータス（`WON`/`PENDING`/`WAITLISTED`/
 * `OFFERED`/`CANCELLED`）を `matchParticipants` に含めて返すため、抜け番算出では
 * 必ず `status === 'WON'` でフィルタしてからペア済み選手を除外する。
 *
 * 旧データとの後方互換のため、エントリが文字列（name単体）の場合は WON とみなす。
 */

/**
 * BulkResultInput 用: 全試合の抜け番をマップで返す。
 * キー=試合番号, 値=抜け番選手の配列 [{ id, name }]
 *
 * @param {Object} sessionData       - { totalMatches, matchParticipants: { [num]: [{ name, status }] } }
 * @param {Array}  allPairings       - [{ matchNumber, player1Id, player2Id }]
 * @param {Array}  allParticipants   - [{ id, name }]（セッション全参加者）
 * @returns {Object} { [matchNumber]: [{ id, name }] }
 */
export function computeByePlayersByMatch(sessionData, allPairings, allParticipants) {
  const totalMatches = sessionData?.totalMatches || 0;
  const result = {};
  for (let num = 1; num <= totalMatches; num++) {
    const matchPairings = allPairings.filter(p => p.matchNumber === num);
    const pairedIds = new Set();
    matchPairings.forEach(p => { pairedIds.add(p.player1Id); pairedIds.add(p.player2Id); });
    const matchPartEntries = sessionData.matchParticipants?.[String(num)] || [];
    const matchPartNames = matchPartEntries
      .filter(p => typeof p === 'string' || p.status === 'WON')
      .map(p => typeof p === 'string' ? p : p.name);
    const bye = allParticipants
      .filter(p => matchPartNames.includes(p.name) && !pairedIds.has(p.id));
    if (bye.length > 0) {
      result[num] = bye.map(p => ({ id: p.id, name: p.name }));
    }
  }
  return result;
}

/**
 * MatchResultsView 用: 指定試合の抜け番選手名の配列を返す。
 *
 * @param {Array}  matchParticipantEntries - session.matchParticipants[matchNumber]（[{ name, status }] もしくは [string]）
 * @param {Array}  matchPairings           - [{ player1Name, player2Name }]
 * @returns {string[]} 抜け番選手名の配列
 */
export function getByePlayerNamesForMatch(matchParticipantEntries, matchPairings) {
  if (!matchParticipantEntries || matchParticipantEntries.length === 0) return [];
  const pairedNames = new Set();
  (matchPairings || []).forEach(p => {
    pairedNames.add(p.player1Name);
    pairedNames.add(p.player2Name);
  });
  return matchParticipantEntries
    .filter(p => typeof p === 'string' || p.status === 'WON')
    .map(p => typeof p === 'string' ? p : p.name)
    .filter(name => !pairedNames.has(name));
}
