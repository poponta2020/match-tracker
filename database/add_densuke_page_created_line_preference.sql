-- LINE 通知設定テーブルに「伝助ページ作成通知」の ON/OFF フラグを追加
-- 既存レコードは全て TRUE で初期化される
ALTER TABLE line_notification_preferences ADD COLUMN densuke_page_created BOOLEAN NOT NULL DEFAULT TRUE;
