-- Kaderu予約取り込み手動トリガー：イベント記録テーブル
-- ADMIN+ ユーザーが /practice 画面から GitHub Actions workflow を起動した記録を保持する。
-- スケジューラーが status=PENDING を巡回し、workflow run の完了で COMPLETED / FAILED に確定する。
CREATE TABLE kaderu_sync_trigger_events (
    id                       BIGSERIAL PRIMARY KEY,
    organization_id          BIGINT NOT NULL REFERENCES organizations(id),
    triggered_by_player_id   BIGINT NOT NULL REFERENCES players(id),
    triggered_at             TIMESTAMP NOT NULL,
    status                   VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    github_run_id            BIGINT,
    completed_at             TIMESTAMP,
    summary                  TEXT,
    failure_reason           TEXT,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 同一団体のPENDINGは常に最大1件であることをDB制約で保証する。
-- アプリ層の事前チェック (findFirstByOrganizationIdAndStatus...) は
-- 同時多重リクエストでは race するため、UNIQUE 部分インデックスで重複起動を防ぐ。
-- 違反は service 層で DataIntegrityViolationException → DuplicateResourceException (409) に変換される。
CREATE UNIQUE INDEX uk_kaderu_sync_pending
    ON kaderu_sync_trigger_events (organization_id)
    WHERE status = 'PENDING';

-- スケジューラーが全PENDINGを古い順に巡回するため
CREATE INDEX idx_kaderu_sync_status_triggered
    ON kaderu_sync_trigger_events (status, triggered_at);

-- line_message_log の notification_type CHECK 制約に Kaderu 同期通知を追加
ALTER TABLE line_message_log DROP CONSTRAINT IF EXISTS line_message_log_notification_type_check;
ALTER TABLE line_message_log ADD CONSTRAINT line_message_log_notification_type_check
    CHECK (notification_type IN (
        'LOTTERY_RESULT',
        'WAITLIST_OFFER',
        'OFFER_EXPIRED',
        'MATCH_PAIRING',
        'PRACTICE_REMINDER',
        'DEADLINE_REMINDER',
        'ADMIN_WAITLIST_UPDATE',
        'WAITLIST_POSITION_UPDATE',
        'SAME_DAY_CONFIRMATION',
        'SAME_DAY_CANCEL',
        'SAME_DAY_VACANCY',
        'ADMIN_SAME_DAY_CONFIRMATION',
        'ADMIN_SAME_DAY_CANCEL',
        'MENTOR_COMMENT',
        'MENTEE_MEMO_UPDATE',
        'DENSUKE_PAGE_CREATED',
        'KADERU_SYNC_COMPLETED',
        'KADERU_SYNC_FAILED'
    ));
