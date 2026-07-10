import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

// react-hooks/rules-of-hooks 修正（PracticeForm → PracticeNewForm 分割）の回帰テスト。
// 新規登録モード・編集モードのどちらも正しくレンダリングされることを確認する。

let mockParams = {};
const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useParams: () => mockParams,
}));

vi.mock('../../api', () => ({
  practiceAPI: {
    getSessionSummaries: vi.fn().mockResolvedValue({ data: [] }),
    getById: vi.fn(),
  },
  venueAPI: {
    getAll: vi.fn().mockResolvedValue({ data: [] }),
  },
  kaderuSyncAPI: {
    getStatus: vi.fn(),
    trigger: vi.fn(),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

import { practiceAPI } from '../../api';
import PracticeForm from './PracticeForm';

describe('PracticeForm', () => {
  beforeEach(() => {
    mockParams = {};
    mockNavigate.mockClear();
    localStorage.setItem('currentPlayer', JSON.stringify({ id: 1, role: 'ADMIN' }));
  });

  afterEach(() => {
    localStorage.clear();
    cleanup();
  });

  it('新規登録モード（/practice/new）ではカレンダーUIを表示する', async () => {
    mockParams = {};

    render(<PracticeForm />);

    await waitFor(() => {
      expect(screen.getByText('日付をタップして会場を選択してください')).toBeInTheDocument();
    });
    expect(mockNavigate).not.toHaveBeenCalledWith('/practice');
  });

  it('編集モード（/practice/:id/edit）では既存フォームを表示する', async () => {
    mockParams = { id: '5' };
    practiceAPI.getById.mockResolvedValue({
      data: { sessionDate: '2026-07-15', venueId: null, totalMatches: 10, notes: '', capacity: null },
    });

    render(<PracticeForm />);

    await waitFor(() => {
      expect(screen.getByText('会場')).toBeInTheDocument();
    });
    expect(practiceAPI.getById).toHaveBeenCalledWith('5');
    expect(mockNavigate).not.toHaveBeenCalledWith('/practice');
  });
});
