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
  getMyResults: (year, month) =>
    apiClient.get('/lottery/my-results', { params: { year, month } }),

  // 参加キャンセル（理由付き・複数対応）
  cancel: (participantId, cancelReason, cancelReasonDetail) =>
    apiClient.post('/lottery/cancel', { participantId, cancelReason, cancelReasonDetail }),

  // 複数参加キャンセル（理由付き）
  cancelMultiple: (participantIds, cancelReason, cancelReasonDetail) =>
    apiClient.post('/lottery/cancel', { participantIds, cancelReason, cancelReasonDetail }),

  // 繰り上げオファーへの応答
  respondOffer: (participantId, accept) =>
    apiClient.post('/lottery/respond-offer', { participantId, accept }),

  // 個別オファー詳細取得
  getOfferDetail: (participantId) =>
    apiClient.get(`/lottery/offer-detail/${participantId}`),

  // キャンセル待ち状況取得
  getWaitlistStatus: () =>
    apiClient.get('/lottery/waitlist-status'),

  // 管理者: 参加者手動編集
  editParticipants: (data) =>
    apiClient.put('/lottery/admin/edit-participants', data),

  // 抽選実行履歴取得
  getExecutions: (year, month) =>
    apiClient.get('/lottery/executions', { params: { year, month } }),

  // 抽選結果通知の送信済みチェック
  notifyStatus: (year, month) =>
    apiClient.get('/lottery/notify-status', { params: { year, month } }),

  // 抽選結果通知の統合送信（アプリ内 + LINE）
  notifyResults: (year, month) =>
    apiClient.post('/lottery/notify-results', { year, month }),

  // キャンセル待ち辞退（セッション単位）
  declineWaitlist: (sessionId, playerId) =>
    apiClient.post('/lottery/decline-waitlist', { sessionId, playerId }),

  // キャンセル待ち復帰（セッション単位）
  rejoinWaitlist: (sessionId, playerId) =>
    apiClient.post('/lottery/rejoin-waitlist', { sessionId, playerId }),

  // 抽選結果確定（伝助一括書き戻しトリガー）
  confirm: (year, month, organizationId) =>
    apiClient.post('/lottery/confirm', { year, month, organizationId }),

  // 抽選プレビュー（DB保存なし）
  preview: (year, month, organizationId) =>
    apiClient.post('/lottery/preview', { year, month, organizationId }),

  // キャンセル待ちのみに通知送信
  notifyWaitlisted: (year, month, organizationId) =>
    apiClient.post('/lottery/notify-waitlisted', { year, month, organizationId }),
};
