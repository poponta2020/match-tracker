import apiClient from './client';

export const lotteryAPI = {
  // 手動抽選実行
  execute: (year, month) =>
    apiClient.post('/lottery/execute', { year, month }),

  // セッション再抽選（priorityPlayerIds: null=直近から引き継ぎ, []=明示クリア, [...]= 上書き指定）
  reExecute: (sessionId, priorityPlayerIds = null) =>
    apiClient.post(`/lottery/re-execute/${sessionId}`,
      priorityPlayerIds !== null ? { priorityPlayerIds } : undefined),

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

  // 繰り上げオファー一括応答
  respondOfferAll: (sessionId, accept) =>
    apiClient.post('/lottery/respond-offer-all', { sessionId, accept }),

  // 個別オファー詳細取得
  getOfferDetail: (participantId) =>
    apiClient.get(`/lottery/offer-detail/${participantId}`),

  // セッション内の自分のOFFERED一覧取得
  getSessionOffers: (sessionId) =>
    apiClient.get(`/lottery/session-offers/${sessionId}`),

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
  notifyStatus: (year, month, organizationId) =>
    apiClient.get('/lottery/notify-status', { params: { year, month, organizationId } }),

  // 指定年月・団体の抽選が確定済みかどうかチェック
  isConfirmed: (year, month, organizationId) =>
    apiClient.get('/lottery/is-confirmed', { params: { year, month, organizationId } }),

  // 抽選結果通知の統合送信（アプリ内 + LINE）
  notifyResults: (year, month, organizationId) =>
    apiClient.post('/lottery/notify-results', { year, month, organizationId }),

  // キャンセル待ち辞退（セッション単位）
  declineWaitlist: (sessionId, playerId) =>
    apiClient.post('/lottery/decline-waitlist', { sessionId, playerId }),

  // キャンセル待ち復帰（セッション単位）
  rejoinWaitlist: (sessionId, playerId) =>
    apiClient.post('/lottery/rejoin-waitlist', { sessionId, playerId }),

  // 抽選結果確定（伝助一括書き戻しトリガー）
  confirm: (year, month, organizationId, seed, priorityPlayerIds = []) =>
    apiClient.post('/lottery/confirm', { year, month, organizationId, seed, priorityPlayerIds }),

  // 抽選プレビュー（DB保存なし）
  preview: (year, month, organizationId, priorityPlayerIds = []) =>
    apiClient.post('/lottery/preview', { year, month, organizationId, priorityPlayerIds }),

  // 対象月・団体で参加希望を出している選手の一覧取得（優先選手指定UI用）
  getMonthlyApplicants: (year, month, organizationId) =>
    apiClient.get('/lottery/monthly-applicants', { params: { year, month, organizationId } }),

  // キャンセル待ちのみに通知送信
  notifyWaitlisted: (year, month, organizationId) =>
    apiClient.post('/lottery/notify-waitlisted', { year, month, organizationId }),
};
