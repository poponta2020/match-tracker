/**
 * ドラッグ&ドロップの状態遷移を計算する純粋関数。
 * React state を直接操作せず、新しい pairings / waitingPlayers を返す。
 *
 * @param {Object} params
 * @param {Object} params.source  - { type: 'pairing'|'waiting', pairingIndex?, position? }
 * @param {Object} params.dest    - { slotType: 'pairing-player1'|'pairing-player2'|'waiting-list'|'new-pairing', pairingIndex? }
 * @param {number} params.draggedPlayerId
 * @param {string} params.draggedPlayerName
 * @param {Array}  params.pairings
 * @param {Array}  params.waitingPlayers
 *
 * @returns {{ pairings: Array, waitingPlayers: Array, affectedPairingIndices: number[] } | null}
 *          null の場合は no-op（同じスロットへのドロップなど）
 */
export function computeDragResult({ source, dest, draggedPlayerId, draggedPlayerName, pairings, waitingPlayers }) {
  const sourceType = source.type;
  const destType = dest.slotType;

  // 同じスロットにドロップ → no-op
  if (sourceType === 'pairing' && (destType === 'pairing-player1' || destType === 'pairing-player2')) {
    const destPosition = destType === 'pairing-player1' ? 1 : 2;
    if (source.pairingIndex === dest.pairingIndex && source.position === destPosition) return null;
  }
  if (sourceType === 'waiting' && destType === 'waiting-list') return null;

  let newPairings = pairings.map(p => ({ ...p }));
  let newWaiting = [...waitingPlayers];
  const affectedPairingIndices = [];

  if (sourceType === 'pairing' && (destType === 'pairing-player1' || destType === 'pairing-player2')) {
    // Case 1: Pairing slot -> Pairing slot
    const srcIdx = source.pairingIndex;
    const srcPos = source.position;
    const dstIdx = dest.pairingIndex;
    const dstPos = destType === 'pairing-player1' ? 1 : 2;

    const srcPairing = newPairings[srcIdx];
    const dstPairing = newPairings[dstIdx];

    const dstPlayerId = dstPos === 1 ? dstPairing.player1Id : dstPairing.player2Id;
    const dstPlayerName = dstPos === 1 ? dstPairing.player1Name : dstPairing.player2Name;

    if (!dstPlayerId) {
      // Move player to empty slot
      if (dstPos === 1) {
        dstPairing.player1Id = draggedPlayerId;
        dstPairing.player1Name = draggedPlayerName;
      } else {
        dstPairing.player2Id = draggedPlayerId;
        dstPairing.player2Name = draggedPlayerName;
      }
      dstPairing.recentMatches = null;
      if (srcPos === 1) {
        srcPairing.player1Id = null;
        srcPairing.player1Name = null;
      } else {
        srcPairing.player2Id = null;
        srcPairing.player2Name = null;
      }
      srcPairing.recentMatches = null;
      if (!srcPairing.player1Id && !srcPairing.player2Id) {
        newPairings.splice(srcIdx, 1);
        affectedPairingIndices.push(dstIdx > srcIdx ? dstIdx - 1 : dstIdx);
      } else {
        affectedPairingIndices.push(srcIdx, dstIdx);
      }
    } else {
      // Swap players
      if (srcPos === 1) {
        srcPairing.player1Id = dstPlayerId;
        srcPairing.player1Name = dstPlayerName;
      } else {
        srcPairing.player2Id = dstPlayerId;
        srcPairing.player2Name = dstPlayerName;
      }
      if (dstPos === 1) {
        dstPairing.player1Id = draggedPlayerId;
        dstPairing.player1Name = draggedPlayerName;
      } else {
        dstPairing.player2Id = draggedPlayerId;
        dstPairing.player2Name = draggedPlayerName;
      }
      srcPairing.recentMatches = null;
      dstPairing.recentMatches = null;
      affectedPairingIndices.push(srcIdx, dstIdx);
    }
  } else if (sourceType === 'waiting' && (destType === 'pairing-player1' || destType === 'pairing-player2')) {
    // Case 2: Waiting -> Pairing slot
    const dstIdx = dest.pairingIndex;
    const dstPos = destType === 'pairing-player1' ? 1 : 2;
    const dstPairing = newPairings[dstIdx];

    const oldPlayerId = dstPos === 1 ? dstPairing.player1Id : dstPairing.player2Id;
    const oldPlayerName = dstPos === 1 ? dstPairing.player1Name : dstPairing.player2Name;

    if (dstPos === 1) {
      dstPairing.player1Id = draggedPlayerId;
      dstPairing.player1Name = draggedPlayerName;
    } else {
      dstPairing.player2Id = draggedPlayerId;
      dstPairing.player2Name = draggedPlayerName;
    }
    dstPairing.recentMatches = null;

    newWaiting = newWaiting.filter(p => p.id !== draggedPlayerId);
    if (oldPlayerId) {
      newWaiting.push({ id: oldPlayerId, name: oldPlayerName });
    }
    affectedPairingIndices.push(dstIdx);
  } else if (sourceType === 'pairing' && destType === 'waiting-list') {
    // Case 3: Pairing slot -> Waiting list
    const srcIdx = source.pairingIndex;
    const srcPos = source.position;
    const srcPairing = newPairings[srcIdx];

    newWaiting.push({ id: draggedPlayerId, name: draggedPlayerName });

    if (srcPos === 1) {
      srcPairing.player1Id = null;
      srcPairing.player1Name = null;
    } else {
      srcPairing.player2Id = null;
      srcPairing.player2Name = null;
    }
    srcPairing.recentMatches = null;

    if (!srcPairing.player1Id && !srcPairing.player2Id) {
      newPairings.splice(srcIdx, 1);
    }
  } else if (sourceType === 'waiting' && destType === 'new-pairing') {
    // Case 4: Waiting -> New pairing zone
    newWaiting = newWaiting.filter(p => p.id !== draggedPlayerId);
    newPairings.push({
      player1Id: draggedPlayerId,
      player1Name: draggedPlayerName,
      player2Id: null,
      player2Name: null,
      recentMatches: null,
    });
  } else {
    return null;
  }

  return { pairings: newPairings, waitingPlayers: newWaiting, affectedPairingIndices };
}
