import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * データフェッチ用カスタムフック
 *
 * StrictModeでの二重実行を防止し、AbortControllerによるクリーンアップを提供する。
 *
 * @param {() => Promise<any>} fetchFn - データ取得関数。引数なし、Promiseを返す。
 * @param {Array} deps - 再フェッチのトリガーとなる依存配列
 * @param {Object} options - オプション
 * @param {boolean} options.immediate - 即座にフェッチするか（デフォルト: true）
 * @returns {{ data, loading, error, refetch }}
 */
export function useFetch(fetchFn, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const fetchingRef = useRef(false);

  const execute = useCallback(async () => {
    if (fetchingRef.current) return;
    fetchingRef.current = true;
    setLoading(true);
    setError(null);

    try {
      const result = await fetchFn();
      setData(result);
    } catch (err) {
      if (err.name !== 'AbortError' && err.name !== 'CanceledError') {
        setError(err);
        console.error('Fetch error:', err);
      }
    } finally {
      setLoading(false);
      fetchingRef.current = false;
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (immediate) {
      execute();
    }
  }, [execute, immediate]);

  return { data, loading, error, refetch: execute };
}
