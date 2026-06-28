import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import PairingHelp from './PairingHelp';

afterEach(cleanup);
beforeEach(() => {
  localStorage.clear();
});

const SECTION_HEADINGS = ['選手の入れ替え方', 'ロックの意味と使い方', '保存の流れ', '日付列の見方'];

describe('PairingHelp（組み合わせ作成 使い方ヘルプ）', () => {
  it('初回訪問（localStorage 未設定）ではパネルが自動表示され、4セクション見出しが見える', () => {
    render(<PairingHelp ready />);
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();
    SECTION_HEADINGS.forEach((heading) =>
      expect(screen.getByText(heading)).toBeInTheDocument()
    );
  });

  it('初回自動表示後に既読フラグ(pairingHelpSeen)が保存される', () => {
    render(<PairingHelp ready />);
    expect(localStorage.getItem('pairingHelpSeen')).toBe('1');
  });

  it('既読（pairingHelpSeen=1）なら初期は閉じており、ボタンで開ける', () => {
    localStorage.setItem('pairingHelpSeen', '1');
    render(<PairingHelp ready />);
    expect(screen.queryByText('この画面の使い方')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '使い方を開く' }));
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();
  });

  it('✕ボタンでパネルを閉じる', () => {
    render(<PairingHelp ready />);
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '使い方を閉じる' }));
    expect(screen.queryByText('この画面の使い方')).not.toBeInTheDocument();
  });

  it('パネル外のタップ（mousedown）で閉じる', () => {
    render(<PairingHelp ready />);
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();

    fireEvent.mouseDown(document.body);
    expect(screen.queryByText('この画面の使い方')).not.toBeInTheDocument();
  });

  it('開いているボタンを再クリックすると閉じる（トグル）', () => {
    localStorage.setItem('pairingHelpSeen', '1');
    render(<PairingHelp ready />);
    const button = screen.getByRole('button', { name: '使い方を開く' });

    fireEvent.click(button); // open
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();
    fireEvent.click(button); // close
    expect(screen.queryByText('この画面の使い方')).not.toBeInTheDocument();
  });

  it('ready=false の間は既読フラグを保存しない（ローディング完了前に既読化しない）', () => {
    render(<PairingHelp ready={false} />);
    // localStorage 未設定のため自動表示はされるが、既読保存は行わない
    expect(screen.getByText('この画面の使い方')).toBeInTheDocument();
    expect(localStorage.getItem('pairingHelpSeen')).toBeNull();
  });
});
