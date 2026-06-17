import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// API 層（barrel）をモック。VideoLibrary は '../../api' から matchVideoAPI / playerAPI を取り込む。
const mocks = vi.hoisted(() => ({
  videoSearch: vi.fn(),
  playerGetAll: vi.fn(),
}));

vi.mock('../../api', () => ({
  matchVideoAPI: {
    search: mocks.videoSearch,
  },
  playerAPI: {
    getAll: mocks.playerGetAll,
  },
}));

import VideoLibrary from './VideoLibrary';

// 手動解決できる deferred Promise（遅延レスポンスの順序を制御するため）
const makeDeferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
};

// 1ページ分の検索レスポンスを組み立てる
const pageResponse = (videos, { page = 0, totalPages = 1, totalElements } = {}) => ({
  data: {
    content: videos,
    page,
    totalPages,
    totalElements: totalElements ?? videos.length,
  },
});

// テスト用の動画レコード（一覧表示に必要な最小フィールド）
const video = (id, p1Name, p2Name) => ({
  id,
  matchDate: '2026-06-12',
  matchNumber: id,
  player1Id: id * 10 + 1,
  player2Id: id * 10 + 2,
  player1Name: p1Name,
  player2Name: p2Name,
  youtubeVideoId: null,
  title: null,
  winnerId: null,
  scoreDifference: null,
});

describe('VideoLibrary - 検索条件変更時の取得競合', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((m) => m.mockReset());
    mocks.playerGetAll.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it('検索条件を変更すると新条件の取得が走り、抑止されない（旧 fetchingRef の即 return が無い）', async () => {
    const user = userEvent.setup();

    // 初回（条件なし）: 解決を保留したままにする
    const first = makeDeferred();
    // 2回目（mine=true）: 即解決
    mocks.videoSearch
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(pageResponse([video(2, '佐藤', '田中')]));

    render(<VideoLibrary />);

    // 初回 fetch が page=0・mine 指定なしで発行される
    await waitFor(() => {
      expect(mocks.videoSearch).toHaveBeenNthCalledWith(1, { page: 0, size: 20 });
    });

    // 初回が未解決（進行中）のまま「自分が関わる動画」トグルで条件変更
    await user.click(screen.getByRole('button', { name: /自分が関わる動画/ }));

    // 進行中でも 2回目（新条件 mine=true）の取得が即発行される（抑止されない）
    await waitFor(() => {
      expect(mocks.videoSearch).toHaveBeenNthCalledWith(2, { page: 0, size: 20, mine: true });
    });

    // 新条件の結果が表示される
    expect(await screen.findByText(/佐藤/)).toBeTruthy();

    // 後から初回（古い条件）のレスポンスが到着しても最終表示を汚染しない
    first.resolve(pageResponse([video(1, '山田', '鈴木')]));
    await waitFor(() => {
      expect(screen.getByText(/佐藤/)).toBeTruthy();
    });
    expect(screen.queryByText(/山田/)).toBeNull();
  });

  it('古い（遅い）レスポンスが新条件の一覧を上書きしない（連番でstale破棄）', async () => {
    const user = userEvent.setup();

    const first = makeDeferred();
    const second = makeDeferred();
    mocks.videoSearch
      .mockReturnValueOnce(first.promise) // 初回（条件なし）
      .mockReturnValueOnce(second.promise); // 2回目（mine=true）

    render(<VideoLibrary />);
    await waitFor(() => expect(mocks.videoSearch).toHaveBeenCalledTimes(1));

    // 条件変更（mine ON）
    await user.click(screen.getByRole('button', { name: /自分が関わる動画/ }));
    await waitFor(() => expect(mocks.videoSearch).toHaveBeenCalledTimes(2));

    // 新条件（2回目）を先に解決
    second.resolve(pageResponse([video(2, '新条件選手', '相手B')]));
    expect(await screen.findByText(/新条件選手/)).toBeTruthy();

    // その後に古い初回レスポンス（別の選手）が遅れて到着
    first.resolve(pageResponse([video(1, '古い選手', '相手A')]));

    // 最終表示は新条件のまま。古い結果は反映されない。
    await waitFor(() => {
      expect(screen.getByText(/新条件選手/)).toBeTruthy();
    });
    expect(screen.queryByText(/古い選手/)).toBeNull();
  });

  it('「もっと見る」の追記中に条件が変わると、古い追記レスポンスは破棄される', async () => {
    const user = userEvent.setup();

    // 初回（条件なし）: 2ページ構成。1件表示 + もっと見る活性
    mocks.videoSearch.mockResolvedValueOnce(
      pageResponse([video(1, '初期選手', '相手1')], { page: 0, totalPages: 2, totalElements: 2 })
    );

    render(<VideoLibrary />);
    expect(await screen.findByText(/初期選手/)).toBeTruthy();

    // 2回目（もっと見る = page:1）: 保留
    const loadMore = makeDeferred();
    // 3回目（mine=true で条件変更）: 即解決
    mocks.videoSearch
      .mockReturnValueOnce(loadMore.promise)
      .mockResolvedValueOnce(
        pageResponse([video(3, '新条件選手', '相手3')], { page: 0, totalPages: 1, totalElements: 1 })
      );

    // もっと見るクリック（追記取得を保留状態にする）
    await user.click(screen.getByRole('button', { name: 'もっと見る' }));
    await waitFor(() => {
      expect(mocks.videoSearch).toHaveBeenNthCalledWith(2, { page: 1, size: 20 });
    });

    // 追記が未解決のまま条件変更（mine ON）→ page=0 再取得が走る
    await user.click(screen.getByRole('button', { name: /自分が関わる動画/ }));
    await waitFor(() => {
      expect(mocks.videoSearch).toHaveBeenNthCalledWith(3, { page: 0, size: 20, mine: true });
    });
    expect(await screen.findByText(/新条件選手/)).toBeTruthy();

    // 遅れて到着した古い追記レスポンスは破棄され、新条件の一覧に append されない
    loadMore.resolve(
      pageResponse([video(2, '古い追記選手', '相手2')], { page: 1, totalPages: 2, totalElements: 2 })
    );
    await waitFor(() => {
      expect(screen.getByText(/新条件選手/)).toBeTruthy();
    });
    expect(screen.queryByText(/古い追記選手/)).toBeNull();
    expect(screen.queryByText(/初期選手/)).toBeNull();
  });

  it('同一条件での「もっと見る」は前ページに追記される（正常系の維持）', async () => {
    const user = userEvent.setup();
    mocks.videoSearch
      .mockResolvedValueOnce(
        pageResponse([video(1, '一人目', '相手1')], { page: 0, totalPages: 2, totalElements: 2 })
      )
      .mockResolvedValueOnce(
        pageResponse([video(2, '二人目', '相手2')], { page: 1, totalPages: 2, totalElements: 2 })
      );

    render(<VideoLibrary />);
    expect(await screen.findByText(/一人目/)).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'もっと見る' }));

    // 追記され、1ページ目・2ページ目が両方表示される
    expect(await screen.findByText(/二人目/)).toBeTruthy();
    expect(screen.getByText(/一人目/)).toBeTruthy();
  });
});
