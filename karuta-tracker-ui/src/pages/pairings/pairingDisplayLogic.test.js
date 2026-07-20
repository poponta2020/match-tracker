import { describe, it, expect } from 'vitest';
import {
  shouldShowParticipantSection,
  resolvePairingEditAction,
  isPairingEditDisabled,
  shouldShowEditingArea,
  shouldShowReshuffleButton,
  shouldShowViewModeUnpairedSection,
  reshuffleButtonLabel,
  isBothCancelled,
  hasAnyCancelled,
  materializeCancelledSlots,
  showsResultLockedRow,
  shouldHideRow,
} from './pairingDisplayLogic';

describe('shouldShowParticipantSection', () => {
  it('組み合わせが空なら表示（新規作成）', () => {
    expect(shouldShowParticipantSection({ pairings: [], isEditing: false })).toBe(true);
  });
  it('既存組が1件でもあれば非表示', () => {
    expect(shouldShowParticipantSection({ pairings: [{ player1Id: 1, player2Id: 2 }], isEditing: false })).toBe(false);
  });
  it('組0件でも編集モード中なら非表示（待機中セクションと二重表示にしない）', () => {
    expect(shouldShowParticipantSection({ pairings: [], isEditing: true })).toBe(false);
  });
});

describe('resolvePairingEditAction', () => {
  it('既存組があれば編集モード切替（auto-match を走らせて保存済みの組を破壊しない）', () => {
    expect(resolvePairingEditAction({
      pairings: [{ player1Id: 1, player2Id: 2 }], participants: [{ id: 1 }, { id: 2 }],
    })).toBe('edit-existing');
  });
  it('結果入力済みの組しか無くても編集モード切替（組が1件でもあれば再生成しない）', () => {
    expect(resolvePairingEditAction({
      pairings: [{ player1Id: 1, player2Id: 2, hasResult: true }], participants: [],
    })).toBe('edit-existing');
  });
  it('組0件・参加者ありなら自動組み合わせ', () => {
    expect(resolvePairingEditAction({ pairings: [], participants: [{ id: 1 }] })).toBe('auto-match');
  });
  it('組0件・参加者0名なら空の編集モード（選手追加への導線を残す）', () => {
    expect(resolvePairingEditAction({ pairings: [], participants: [] })).toBe('empty-edit');
  });
});

describe('isPairingEditDisabled', () => {
  it('新規作成の閲覧状態では押せる', () => {
    expect(isPairingEditDisabled({ isReadOnly: false, isEditing: false, isViewMode: false })).toBe(false);
  });
  it('既存組の閲覧モードでは押せる', () => {
    expect(isPairingEditDisabled({ isReadOnly: false, isEditing: false, isViewMode: true })).toBe(false);
  });
  it('既に編集モードに入っていれば無効（非表示にはせず高さを保つ）', () => {
    expect(isPairingEditDisabled({ isReadOnly: false, isEditing: true, isViewMode: false })).toBe(true);
  });
  it('他試合に未保存変更があり閲覧専用なら無効', () => {
    expect(isPairingEditDisabled({ isReadOnly: true, isEditing: false, isViewMode: false })).toBe(true);
  });
});

describe('shouldShowEditingArea', () => {
  it('組が1件以上あれば描画（閲覧モードの表示も含む）', () => {
    expect(shouldShowEditingArea({ pairings: [{ player1Id: 1 }], isEditing: false })).toBe(true);
  });
  it('組0件でも編集モードなら描画（待機中セクションの「選手追加」に到達できるように）', () => {
    expect(shouldShowEditingArea({ pairings: [], isEditing: true })).toBe(true);
  });
  it('組0件かつ非編集なら描画しない', () => {
    expect(shouldShowEditingArea({ pairings: [], isEditing: false })).toBe(false);
  });
});

describe('shouldShowReshuffleButton', () => {
  it('組み合わせが1件以上・編集可能状態なら表示（AC-1）', () => {
    expect(shouldShowReshuffleButton({
      isReadOnly: false, isViewMode: false, pairings: [{ player1Id: 1, player2Id: 2 }],
    })).toBe(true);
  });
  it('組み合わせが空なら非表示（新規作成は対戦編集ボタン側）', () => {
    expect(shouldShowReshuffleButton({ isReadOnly: false, isViewMode: false, pairings: [] })).toBe(false);
  });
  it('閲覧モードでは非表示（AC-1）', () => {
    expect(shouldShowReshuffleButton({
      isReadOnly: false, isViewMode: true, pairings: [{ player1Id: 1, player2Id: 2 }],
    })).toBe(false);
  });
  it('閲覧専用（他試合に未保存変更）では非表示（AC-1）', () => {
    expect(shouldShowReshuffleButton({
      isReadOnly: true, isViewMode: false, pairings: [{ player1Id: 1, player2Id: 2 }],
    })).toBe(false);
  });
});

describe('shouldShowViewModeUnpairedSection（閲覧時の未組み合わせチップ表示可否）', () => {
  const pairing = [{ player1Id: 1, player2Id: 2 }];
  const waiting = [{ id: 3, name: '田中' }];

  it('閲覧モード・組あり・待機あり → true（AC-1）', () => {
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: false, isViewMode: true, pairings: pairing, waitingPlayers: waiting,
    })).toBe(true);
  });
  it('読み取り専用モード・組あり・待機あり → true（AC-2）', () => {
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: true, isViewMode: false, pairings: pairing, waitingPlayers: waiting,
    })).toBe(true);
  });
  it('組0件なら isViewMode/isReadOnly に関わらず false（参加者一覧との二重表示防止・AC-3）', () => {
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: false, isViewMode: true, pairings: [], waitingPlayers: waiting,
    })).toBe(false);
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: true, isViewMode: false, pairings: [], waitingPlayers: waiting,
    })).toBe(false);
  });
  it('待機0名なら false（空セクションを出さない・AC-4）', () => {
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: false, isViewMode: true, pairings: pairing, waitingPlayers: [],
    })).toBe(false);
  });
  it('編集モード（isReadOnly:false かつ isViewMode:false）なら常に false（編集の待機中セクションと相互排他・AC-5）', () => {
    expect(shouldShowViewModeUnpairedSection({
      isReadOnly: false, isViewMode: false, pairings: pairing, waitingPlayers: waiting,
    })).toBe(false);
  });
});

describe('reshuffleButtonLabel（ロック有無による動的文言・AC-2）', () => {
  it('ロック済みの組（locked）が1件以上 → 「ロックされた組以外をシャッフル」', () => {
    expect(reshuffleButtonLabel([
      { player1Id: 1, player2Id: 2, locked: true },
      { player1Id: 3, player2Id: 4 },
    ])).toBe('ロックされた組以外をシャッフル');
  });
  it('結果入力済み（hasResult）が1件以上 → 「ロックされた組以外をシャッフル」', () => {
    expect(reshuffleButtonLabel([
      { player1Id: 1, player2Id: 2, hasResult: true },
    ])).toBe('ロックされた組以外をシャッフル');
  });
  it('ロック0件 → 「再シャッフル」', () => {
    expect(reshuffleButtonLabel([
      { player1Id: 1, player2Id: 2 },
      { player1Id: 3, player2Id: 4, locked: false, hasResult: false },
    ])).toBe('再シャッフル');
  });
  it('空配列・null → 「再シャッフル」', () => {
    expect(reshuffleButtonLabel([])).toBe('再シャッフル');
    expect(reshuffleButtonLabel(null)).toBe('再シャッフル');
  });
});

describe('対戦相手キャンセル判定（pairing-cancelled-opponent）', () => {
  describe('isBothCancelled', () => {
    it('両方キャンセル → true', () => {
      expect(isBothCancelled({ player1Cancelled: true, player2Cancelled: true })).toBe(true);
    });
    it('片方のみキャンセル → false', () => {
      expect(isBothCancelled({ player1Cancelled: true, player2Cancelled: false })).toBe(false);
      expect(isBothCancelled({ player1Cancelled: false, player2Cancelled: true })).toBe(false);
    });
    it('キャンセルなし → false', () => {
      expect(isBothCancelled({ player1Cancelled: false, player2Cancelled: false })).toBe(false);
    });
    it('フラグ未定義（後方互換）→ false', () => {
      expect(isBothCancelled({})).toBe(false);
      expect(isBothCancelled(null)).toBe(false);
      expect(isBothCancelled(undefined)).toBe(false);
    });
  });

  describe('hasAnyCancelled', () => {
    it('片方でもキャンセル → true', () => {
      expect(hasAnyCancelled({ player1Cancelled: true, player2Cancelled: false })).toBe(true);
      expect(hasAnyCancelled({ player1Cancelled: false, player2Cancelled: true })).toBe(true);
    });
    it('両方キャンセル → true', () => {
      expect(hasAnyCancelled({ player1Cancelled: true, player2Cancelled: true })).toBe(true);
    });
    it('キャンセルなし → false', () => {
      expect(hasAnyCancelled({ player1Cancelled: false, player2Cancelled: false })).toBe(false);
    });
    it('フラグ未定義（後方互換）→ false', () => {
      expect(hasAnyCancelled({})).toBe(false);
      expect(hasAnyCancelled(null)).toBe(false);
    });
  });

  describe('materializeCancelledSlots', () => {
    it('片方キャンセル → 該当スロットを null 化しフラグ解除（生存側は不変）', () => {
      const pairings = [
        { id: 1, player1Id: 10, player1Name: '鈴木', player1Cancelled: false, player2Id: 20, player2Name: '山田', player2Cancelled: true, locked: false },
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result).toHaveLength(1);
      // 生存側（player1）はそのまま
      expect(result[0].player1Id).toBe(10);
      expect(result[0].player1Name).toBe('鈴木');
      expect(result[0].player1Cancelled).toBe(false);
      // キャンセル側（player2）は空きスロット化＋フラグ解除
      expect(result[0].player2Id).toBeNull();
      expect(result[0].player2Name).toBeNull();
      expect(result[0].player2Cancelled).toBe(false);
    });

    it('player1 側キャンセルでも左スロットを null 化する', () => {
      const pairings = [
        { player1Id: 10, player1Name: '鈴木', player1Cancelled: true, player2Id: 20, player2Name: '山田', player2Cancelled: false },
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result[0].player1Id).toBeNull();
      expect(result[0].player1Name).toBeNull();
      expect(result[0].player1Cancelled).toBe(false);
      expect(result[0].player2Id).toBe(20);
      expect(result[0].player2Cancelled).toBe(false);
    });

    it('両方キャンセル → 組ごと除去', () => {
      const pairings = [
        { player1Id: 10, player1Name: 'A', player1Cancelled: true, player2Id: 20, player2Name: 'B', player2Cancelled: true },
        { player1Id: 30, player1Name: 'C', player1Cancelled: false, player2Id: 40, player2Name: 'D', player2Cancelled: false },
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result).toHaveLength(1);
      expect(result[0].player1Id).toBe(30);
      expect(result[0].player2Id).toBe(40);
    });

    it('通常組（キャンセルなし）は内容不変', () => {
      const pairings = [
        { player1Id: 30, player1Name: 'C', player1Cancelled: false, player2Id: 40, player2Name: 'D', player2Cancelled: false, locked: true, hasResult: false },
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result[0]).toEqual(pairings[0]);
    });

    it('イミュータブル: 元配列・元オブジェクトを破壊しない', () => {
      const original = { player1Id: 10, player1Name: '鈴木', player1Cancelled: false, player2Id: 20, player2Name: '山田', player2Cancelled: true };
      const pairings = [original];
      const result = materializeCancelledSlots(pairings);
      // 元は不変
      expect(original.player2Id).toBe(20);
      expect(original.player2Cancelled).toBe(true);
      // 新しい参照
      expect(result[0]).not.toBe(original);
      expect(result).not.toBe(pairings);
    });

    it('複数組の混在（両/片/通常）を正しく処理する', () => {
      const pairings = [
        { player1Id: 1, player1Name: 'A', player1Cancelled: true, player2Id: 2, player2Name: 'B', player2Cancelled: true },   // 両 → 除去
        { player1Id: 3, player1Name: 'C', player1Cancelled: false, player2Id: 4, player2Name: 'D', player2Cancelled: true },  // 片 → D を空きに
        { player1Id: 5, player1Name: 'E', player1Cancelled: false, player2Id: 6, player2Name: 'F', player2Cancelled: false }, // 通常 → 不変
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result).toHaveLength(2);
      expect(result[0].player1Id).toBe(3);
      expect(result[0].player2Id).toBeNull();
      expect(result[1].player1Id).toBe(5);
      expect(result[1].player2Id).toBe(6);
    });

    it('空配列・null 入力でも例外を投げず空配列を返す', () => {
      expect(materializeCancelledSlots([])).toEqual([]);
      expect(materializeCancelledSlots(null)).toEqual([]);
      expect(materializeCancelledSlots(undefined)).toEqual([]);
    });

    it('結果入力済みはそのまま保持・手動ロック組はキャンセルで空き化しロック解除する', () => {
      const pairings = [
        { player1Id: 1, player2Id: 2, player2Cancelled: true, hasResult: true },                              // 結果入力済み → 不変
        { player1Id: 3, player2Id: 4, player2Cancelled: true, locked: true },                                 // 手動ロック → 空き化＋解除
        { player1Id: 5, player2Id: 6, player1Cancelled: true, player2Cancelled: true, hasResult: true },      // 両キャンセルでも結果入力済みは残す
      ];
      const result = materializeCancelledSlots(pairings);
      expect(result).toHaveLength(3);
      expect(result[0].player2Id).toBe(2);
      expect(result[0].player2Cancelled).toBe(true);
      expect(result[1].player2Id).toBeNull();
      expect(result[1].player2Cancelled).toBe(false);
      expect(result[1].locked).toBe(false);
      expect(result[1].cancelledEmptied).toBe(true); // 保存ボタンの未完成ガードから除外するマーカー
      expect(result[0].cancelledEmptied).toBeFalsy(); // 結果入力済み組には付けない
      expect(result[2].player1Id).toBe(5);
    });
  });

  describe('showsResultLockedRow（行描画の優先順位）', () => {
    it('結果入力済みは常に true（キャンセルがあっても結果が正）', () => {
      expect(showsResultLockedRow({ hasResult: true })).toBe(true);
      expect(showsResultLockedRow({ hasResult: true, player2Cancelled: true })).toBe(true);
    });
    it('手動ロックはキャンセルが無いときのみ true（片方キャンセルがあれば false）', () => {
      expect(showsResultLockedRow({ locked: true })).toBe(true);
      expect(showsResultLockedRow({ locked: true, player1Cancelled: true })).toBe(false);
    });
    it('結果なし・ロックなしは false', () => {
      expect(showsResultLockedRow({})).toBe(false);
      expect(showsResultLockedRow({ player2Cancelled: true })).toBe(false);
      expect(showsResultLockedRow(null)).toBe(false);
    });
  });

  describe('shouldHideRow（閲覧モードの行非表示）', () => {
    it('両方キャンセルは非表示（true）', () => {
      expect(shouldHideRow({ player1Cancelled: true, player2Cancelled: true })).toBe(true);
    });
    it('両方キャンセルでも結果入力済みは残す（false）', () => {
      expect(shouldHideRow({ player1Cancelled: true, player2Cancelled: true, hasResult: true })).toBe(false);
    });
    it('片方キャンセル・通常は非表示にしない（false）', () => {
      expect(shouldHideRow({ player2Cancelled: true })).toBe(false);
      expect(shouldHideRow({})).toBe(false);
      expect(shouldHideRow(null)).toBe(false);
    });
  });
});
