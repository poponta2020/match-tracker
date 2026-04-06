import { describe, it, expect } from 'vitest';
import { computeDragResult } from './pairingDragLogic';

const makePairing = (p1Id, p1Name, p2Id, p2Name) => ({
  player1Id: p1Id, player1Name: p1Name,
  player2Id: p2Id, player2Name: p2Name,
  recentMatches: [{ matchDate: '2026-01-01' }],
});

describe('computeDragResult', () => {
  describe('no-op cases', () => {
    it('returns null when dropping on same slot', () => {
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'pairing-player1', pairingIndex: 0 },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [],
      });
      expect(result).toBeNull();
    });

    it('returns null when dropping waiting player back to waiting list', () => {
      const result = computeDragResult({
        source: { type: 'waiting' },
        dest: { slotType: 'waiting-list' },
        draggedPlayerId: 3, draggedPlayerName: 'C',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [{ id: 3, name: 'C' }],
      });
      expect(result).toBeNull();
    });
  });

  describe('Case 1: pairing <-> pairing swap', () => {
    it('swaps two players between pairings', () => {
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'pairing-player2', pairingIndex: 1 },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: [makePairing(1, 'A', 2, 'B'), makePairing(3, 'C', 4, 'D')],
        waitingPlayers: [],
      });
      expect(result.pairings[0].player1Id).toBe(4);
      expect(result.pairings[0].player1Name).toBe('D');
      expect(result.pairings[1].player2Id).toBe(1);
      expect(result.pairings[1].player2Name).toBe('A');
      expect(result.pairings[0].recentMatches).toBeNull();
      expect(result.pairings[1].recentMatches).toBeNull();
      expect(result.affectedPairingIndices).toEqual([0, 1]);
    });

    it('swaps within same pairing (left-right)', () => {
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'pairing-player2', pairingIndex: 0 },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [],
      });
      expect(result.pairings[0].player1Id).toBe(2);
      expect(result.pairings[0].player2Id).toBe(1);
    });

    it('moves player to empty slot and clears recentMatches', () => {
      const pairings = [
        makePairing(1, 'A', 2, 'B'),
        { player1Id: 3, player1Name: 'C', player2Id: null, player2Name: null, recentMatches: null },
      ];
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 2 },
        dest: { slotType: 'pairing-player2', pairingIndex: 1 },
        draggedPlayerId: 2, draggedPlayerName: 'B',
        pairings, waitingPlayers: [],
      });
      expect(result.pairings[1].player2Id).toBe(2);
      expect(result.pairings[0].player2Id).toBeNull();
      expect(result.pairings[0].recentMatches).toBeNull();
      expect(result.pairings[1].recentMatches).toBeNull();
    });

    it('removes source row when both slots become empty after move to empty slot', () => {
      const pairings = [
        { player1Id: null, player1Name: null, player2Id: 2, player2Name: 'B', recentMatches: null },
        { player1Id: 3, player1Name: 'C', player2Id: null, player2Name: null, recentMatches: null },
      ];
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 2 },
        dest: { slotType: 'pairing-player2', pairingIndex: 1 },
        draggedPlayerId: 2, draggedPlayerName: 'B',
        pairings, waitingPlayers: [],
      });
      expect(result.pairings).toHaveLength(1);
      expect(result.pairings[0].player1Id).toBe(3);
      expect(result.pairings[0].player2Id).toBe(2);
      // Index adjusted because source row (idx 0) was removed
      expect(result.affectedPairingIndices).toEqual([0]);
    });
  });

  describe('Case 2: waiting -> pairing', () => {
    it('replaces pairing player with waiting player', () => {
      const result = computeDragResult({
        source: { type: 'waiting' },
        dest: { slotType: 'pairing-player1', pairingIndex: 0 },
        draggedPlayerId: 3, draggedPlayerName: 'C',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [{ id: 3, name: 'C' }],
      });
      expect(result.pairings[0].player1Id).toBe(3);
      expect(result.pairings[0].player1Name).toBe('C');
      expect(result.pairings[0].recentMatches).toBeNull();
      expect(result.waitingPlayers).toEqual([{ id: 1, name: 'A' }]);
    });

    it('fills empty slot without pushing old player to waiting', () => {
      const pairings = [
        { player1Id: 1, player1Name: 'A', player2Id: null, player2Name: null, recentMatches: null },
      ];
      const result = computeDragResult({
        source: { type: 'waiting' },
        dest: { slotType: 'pairing-player2', pairingIndex: 0 },
        draggedPlayerId: 3, draggedPlayerName: 'C',
        pairings,
        waitingPlayers: [{ id: 3, name: 'C' }],
      });
      expect(result.pairings[0].player2Id).toBe(3);
      expect(result.waitingPlayers).toEqual([]);
    });
  });

  describe('Case 3: pairing -> waiting', () => {
    it('moves player to waiting and leaves empty slot', () => {
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'waiting-list' },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [],
      });
      expect(result.pairings[0].player1Id).toBeNull();
      expect(result.pairings[0].player2Id).toBe(2);
      expect(result.pairings[0].recentMatches).toBeNull();
      expect(result.waitingPlayers).toEqual([{ id: 1, name: 'A' }]);
    });

    it('removes row when both slots become empty', () => {
      const pairings = [
        { player1Id: null, player1Name: null, player2Id: 2, player2Name: 'B', recentMatches: null },
      ];
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 2 },
        dest: { slotType: 'waiting-list' },
        draggedPlayerId: 2, draggedPlayerName: 'B',
        pairings, waitingPlayers: [],
      });
      expect(result.pairings).toHaveLength(0);
      expect(result.waitingPlayers).toEqual([{ id: 2, name: 'B' }]);
    });
  });

  describe('Case 4: waiting -> new-pairing', () => {
    it('creates a new pairing with one player', () => {
      const result = computeDragResult({
        source: { type: 'waiting' },
        dest: { slotType: 'new-pairing' },
        draggedPlayerId: 3, draggedPlayerName: 'C',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [{ id: 3, name: 'C' }, { id: 4, name: 'D' }],
      });
      expect(result.pairings).toHaveLength(2);
      expect(result.pairings[1].player1Id).toBe(3);
      expect(result.pairings[1].player2Id).toBeNull();
      expect(result.pairings[1].recentMatches).toBeNull();
      expect(result.waitingPlayers).toEqual([{ id: 4, name: 'D' }]);
    });
  });

  describe('unsupported combinations', () => {
    it('returns null for pairing -> new-pairing', () => {
      const result = computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'new-pairing' },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: [makePairing(1, 'A', 2, 'B')],
        waitingPlayers: [],
      });
      expect(result).toBeNull();
    });
  });

  describe('does not mutate input', () => {
    it('original pairings remain unchanged', () => {
      const original = [makePairing(1, 'A', 2, 'B'), makePairing(3, 'C', 4, 'D')];
      const originalCopy = JSON.parse(JSON.stringify(original));
      computeDragResult({
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        dest: { slotType: 'pairing-player2', pairingIndex: 1 },
        draggedPlayerId: 1, draggedPlayerName: 'A',
        pairings: original, waitingPlayers: [],
      });
      expect(original).toEqual(originalCopy);
    });
  });
});
