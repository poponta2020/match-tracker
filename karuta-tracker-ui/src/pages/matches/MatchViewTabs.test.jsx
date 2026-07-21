import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import MatchViewTabs from './MatchViewTabs';

describe('MatchViewTabs（戦績確認画面のタブ帯）', () => {
  afterEach(() => cleanup());

  it('タブ順は「カレンダー」（左）／「戦績確認」（右）', () => {
    render(<MatchViewTabs active="record" onChange={() => {}} />);
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toHaveTextContent('カレンダー');
    expect(buttons[1]).toHaveTextContent('戦績確認');
  });

  it('アクティブなタブに下線（border-b-2 border-[#4a6b5a]）が付く', () => {
    render(<MatchViewTabs active="calendar" onChange={() => {}} />);
    const calendarTab = screen.getByRole('button', { name: 'カレンダー' });
    const recordTab = screen.getByRole('button', { name: '戦績確認' });
    expect(calendarTab.className).toContain('border-[#4a6b5a]');
    expect(calendarTab.className).toContain('font-bold');
    expect(recordTab.className).toContain('border-transparent');
    expect(recordTab.className).not.toContain('font-bold');
  });

  it('タブ押下で onChange にそのキーを渡す', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<MatchViewTabs active="record" onChange={onChange} />);

    await user.click(screen.getByRole('button', { name: 'カレンダー' }));
    expect(onChange).toHaveBeenCalledWith('calendar');

    await user.click(screen.getByRole('button', { name: '戦績確認' }));
    expect(onChange).toHaveBeenCalledWith('record');
  });
});
