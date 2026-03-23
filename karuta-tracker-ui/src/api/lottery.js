import apiClient from './client';

export const lotteryAPI = {
  // 手動抽選実行
  execute: (year, month) =>
    apiClient.post('/lottery/execute', { year, month }),

  // セッション再抽選
  reExecute: (sessionId) =>
    apiClient.post(`/lottery/re-execute/${sessionId}`),

  // 月別抽選結果取得
  getResults: (year, month) =>
    apiClient.get('/lottery/results', { params: { year, month } }),

  // セッション別抽選結果取得
  getSessionResult: (sessionId) =>
    apiClient.get(`/lottery/results/${sessionId}`),

  // 自分の抽選結果取得
  getMyResults: (year, month, playerId) =>
    apiClient.get('/lottery/my-results', { params: { year, month, playerId } }),

  // 参加キャンセル
  cancel: (participantId) =>
    apiClient.post('/lottery/cancel', { participantId }),

  // 繰り上げオファーへの応答
  respondOffer: (participantId, accept) =>
    apiClient.post('/lottery/respond-offer', { participantId, accept }),

  // キャンセル待ち状況取得
  getWaitlistStatus: (playerId) =>
    apiClient.get('/lottery/waitlist-status', { params: { playerId } }),

  // 管理者: 参加者手動編集
  editParticipants: (data) =>
    apiClient.put('/lottery/admin/edit-participants', data),

  // 抽選実行履歴取得
  getExecutions: (year, month) =>
    apiClient.get('/lottery/executions', { params: { year, month } }),
};
