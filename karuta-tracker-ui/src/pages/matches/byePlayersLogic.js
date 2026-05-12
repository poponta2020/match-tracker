/**
 * 抜け番算出ロジック（純粋関数）
 *
 * バックエンドの `PracticeSessionService.enrichDtoWithMatchDetails` は
 * `DECLINED` / `WAITLIST_DECLINED` 以外の全ステータス（`WON`/`PENDING`/`WAITLISTED`/
 * `OFFERED`/`CANCELLED`）を `matchParticipants` に含めて返すため、抜け番算出では
 * 組み合わせ対象となるアクティブ参加者でフィルタしてからペア済み選手を除外する。
 *
 * 組み合わせ対象ステータスは団体の運用設定により異なる:
 *  - 抽選あり運用 (MONTHLY + 締め切りあり): WON のみ
 *  - 抽選なし運用 (SAME_DAY もしくは MONTHLY + 締め切りなし): WON + PENDING
 *
 * バックエンドは `PracticeSessionDto.pairingIncludesPending` で「PENDING を含めるか」を
 * 返すため、フロント側はこのフラグだけを見て判定する（バックエンドと同一ルール）。
 *
 * 旧データとの後方互換のため、エントリが文字列（name単体）の場合は組み合わせ対象とみなす。
 */

function isPairingTarget(entry, includesPending) {
  if (typeof entry === 'string') return true;
  if (entry.status === 'WON') return true;
  if (entry.status === 'PENDING') return includesPending;
  return false;
}

/**
 * BulkResultInput 用: 全試合の抜け番をマップで返す。
 * キー=試合番号, 値=抜け番選手の配列 [{ id, name }]
 *
 * @param {Object} sessionData       - { totalMatches, matchParticipants, pairingIncludesPending }
 * @param {Array}  allPairings       - [{ matchNumber, player1Id, player2Id }]
 * @param {Array}  allParticipants   - [{ id, name }]（セッション全参加者）
 * @returns {Object} { [matchNumber]: [{ id, name }] }
 */
export function computeByePlayersByMatch(sessionData, allPairings, allParticipants) {
  const totalMatches = sessionData?.totalMatches || 0;
  const includesPending = sessionData?.pairingIncludesPending === true;
  const result = {};
  for (let num = 1; num <= totalMatches; num++) {
    const matchPairings = allPairings.filter(p => p.matchNumber === num);
    const pairedIds = new Set();
    matchPairings.forEach(p => { pairedIds.add(p.player1Id); pairedIds.add(p.player2Id); });
    const matchPartEntries = sessionData.matchParticipants?.[String(num)] || [];
    const matchPartNames = matchPartEntries
      .filter(p => isPairingTarget(p, includesPending))
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
 * @param {Array}   matchParticipantEntries - session.matchParticipants[matchNumber]（[{ name, status }] もしくは [string]）
 * @param {Array}   matchPairings           - [{ player1Name, player2Name }]
 * @param {boolean} pairingIncludesPending  - PENDING を組み合わせ対象に含めるか（抽選なし運用のとき true）
 * @returns {string[]} 抜け番選手名の配列
 */
export function getByePlayerNamesForMatch(matchParticipantEntries, matchPairings, pairingIncludesPending = false) {
  if (!matchParticipantEntries || matchParticipantEntries.length === 0) return [];
  const pairedNames = new Set();
  (matchPairings || []).forEach(p => {
    pairedNames.add(p.player1Name);
    pairedNames.add(p.player2Name);
  });
  return matchParticipantEntries
    .filter(p => isPairingTarget(p, pairingIncludesPending === true))
    .map(p => typeof p === 'string' ? p : p.name)
    .filter(name => !pairedNames.has(name));
}
