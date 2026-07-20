package com.karuta.matchtracker.dto;

/**
 * ワーカーからの「LINEセッション(SSO)失効が近い」先回り警告リクエスト（line-chat-auto-relogin タスク2）。
 *
 * <p>ワーカーは自身の in-memory context が保持する {@code __is_login_sso} の失効日時を各サイクルで参照し、
 * 閾値（既定3日）以内になったら当エンドポイントへ通知する。アプリは有効な配信グループの各団体の管理者へ
 * 既存の {@code ADMIN_CHAT_RESERVE_ALERT} でリレーするだけで、状態は保持しない（throttle はワーカー側 in-memory）。
 *
 * @param daysRemaining SSO Cookie 失効までの残り日数（ワーカーが実 Cookie 期限から算出。0以下＝直前/失効）
 */
public record LineChatWorkerSessionWarningRequest(
        Integer daysRemaining) {
}
