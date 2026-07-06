import apiClient from './client';

/**
 * 札ルール再生成カウンタ(nonce)のDB共有API。
 * 出札50枚を全端末で一致させるため、日付ごとの nonce をDBで共有する。
 */
export const cardRuleNonceAPI = {
  // 日付の nonce を取得（未登録日は { date, nonce: 0 }）
  getByDate: (date) => apiClient.get('/card-rule-nonce', { params: { date } }),

  // 日付の nonce を更新（「札を再生成」時）
  update: (date, nonce) => apiClient.put('/card-rule-nonce', { date, nonce }),
};
