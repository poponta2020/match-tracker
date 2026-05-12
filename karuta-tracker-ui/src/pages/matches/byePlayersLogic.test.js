import { describe, it, expect } from 'vitest';
import { computeByePlayersByMatch, getByePlayerNamesForMatch } from './byePlayersLogic';

describe('computeByePlayersByMatch (BulkResultInput)', () => {
  it('returns only WON participants who are not paired as bye', () => {
    const sessionData = {
      totalMatches: 1,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'B', status: 'WON' },
          { name: 'C', status: 'WON' },
        ],
      },
    };
    const allPairings = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
    ];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
      { id: 3, name: 'C' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toEqual([{ id: 3, name: 'C' }]);
  });

  it('excludes CANCELLED participants from bye', () => {
    const sessionData = {
      totalMatches: 1,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'B', status: 'WON' },
          { name: 'C', status: 'WON' },
          { name: 'D', status: 'CANCELLED' },
        ],
      },
    };
    const allPairings = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
    ];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
      { id: 3, name: 'C' },
      { id: 4, name: 'D' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toEqual([{ id: 3, name: 'C' }]);
    expect(result[1].find(p => p.name === 'D')).toBeUndefined();
  });

  it('抽選なし運用 (pairingIncludesPending=true): PENDING を組み合わせ対象に含める', () => {
    const sessionData = {
      totalMatches: 1,
      pairingIncludesPending: true,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'B', status: 'WAITLISTED' },
          { name: 'C', status: 'OFFERED' },
          { name: 'D', status: 'PENDING' },
        ],
      },
    };
    const allPairings = [];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
      { id: 3, name: 'C' },
      { id: 4, name: 'D' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toEqual([{ id: 1, name: 'A' }, { id: 4, name: 'D' }]);
  });

  it('抽選あり運用 (pairingIncludesPending=false): PENDING は組み合わせ対象から除外する', () => {
    const sessionData = {
      totalMatches: 1,
      pairingIncludesPending: false,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'B', status: 'WAITLISTED' },
          { name: 'C', status: 'OFFERED' },
          { name: 'D', status: 'PENDING' },
        ],
      },
    };
    const allPairings = [];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
      { id: 3, name: 'C' },
      { id: 4, name: 'D' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toEqual([{ id: 1, name: 'A' }]);
  });

  it('pairingIncludesPending 未指定時は安全側 (PENDING を除外) で動作する', () => {
    const sessionData = {
      totalMatches: 1,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'D', status: 'PENDING' },
        ],
      },
    };
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 4, name: 'D' },
    ];

    const result = computeByePlayersByMatch(sessionData, [], allParticipants);
    expect(result[1]).toEqual([{ id: 1, name: 'A' }]);
  });

  it('treats string entries as WON (backward compatibility)', () => {
    const sessionData = {
      totalMatches: 1,
      matchParticipants: { '1': ['A', 'B'] },
    };
    const allPairings = [];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toEqual([{ id: 1, name: 'A' }, { id: 2, name: 'B' }]);
  });

  it('omits match number with no bye candidates', () => {
    const sessionData = {
      totalMatches: 1,
      matchParticipants: {
        '1': [
          { name: 'A', status: 'WON' },
          { name: 'B', status: 'WON' },
        ],
      },
    };
    const allPairings = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
    ];
    const allParticipants = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
    ];

    const result = computeByePlayersByMatch(sessionData, allPairings, allParticipants);
    expect(result[1]).toBeUndefined();
  });

  it('handles missing matchParticipants gracefully', () => {
    const sessionData = { totalMatches: 1 };
    const result = computeByePlayersByMatch(sessionData, [], []);
    expect(result).toEqual({});
  });
});

describe('getByePlayerNamesForMatch (MatchResultsView)', () => {
  it('returns only WON names not in any pairing', () => {
    const entries = [
      { name: 'A', status: 'WON' },
      { name: 'B', status: 'WON' },
      { name: 'C', status: 'WON' },
    ];
    const pairings = [{ player1Name: 'A', player2Name: 'B' }];
    expect(getByePlayerNamesForMatch(entries, pairings)).toEqual(['C']);
  });

  it('excludes CANCELLED participants from bye names', () => {
    const entries = [
      { name: 'A', status: 'WON' },
      { name: 'B', status: 'WON' },
      { name: 'C', status: 'WON' },
      { name: 'D', status: 'CANCELLED' },
    ];
    const pairings = [{ player1Name: 'A', player2Name: 'B' }];
    const result = getByePlayerNamesForMatch(entries, pairings);
    expect(result).toEqual(['C']);
    expect(result).not.toContain('D');
  });

  it('抽選なし運用 (pairingIncludesPending=true): PENDING を組み合わせ対象に含める', () => {
    const entries = [
      { name: 'A', status: 'WON' },
      { name: 'B', status: 'WAITLISTED' },
      { name: 'C', status: 'OFFERED' },
      { name: 'D', status: 'PENDING' },
    ];
    expect(getByePlayerNamesForMatch(entries, [], true)).toEqual(['A', 'D']);
  });

  it('抽選あり運用 (pairingIncludesPending=false): PENDING は除外する', () => {
    const entries = [
      { name: 'A', status: 'WON' },
      { name: 'B', status: 'WAITLISTED' },
      { name: 'C', status: 'OFFERED' },
      { name: 'D', status: 'PENDING' },
    ];
    expect(getByePlayerNamesForMatch(entries, [], false)).toEqual(['A']);
  });

  it('pairingIncludesPending 未指定時は安全側 (PENDING を除外) で動作する', () => {
    const entries = [
      { name: 'A', status: 'WON' },
      { name: 'D', status: 'PENDING' },
    ];
    expect(getByePlayerNamesForMatch(entries, [])).toEqual(['A']);
  });

  it('treats string entries as WON (backward compatibility)', () => {
    expect(getByePlayerNamesForMatch(['A', 'B'], [])).toEqual(['A', 'B']);
  });

  it('returns empty array when entries are empty', () => {
    expect(getByePlayerNamesForMatch([], [])).toEqual([]);
    expect(getByePlayerNamesForMatch(null, [])).toEqual([]);
  });
});
