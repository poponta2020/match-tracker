-- 隣室空き確認通知機能: 新規テーブル追加
-- room_availability_cache: スクレイピング結果のキャッシュ
-- adjacent_room_notifications: 段階的通知の重複防止

-- スクレイピング結果キャッシュテーブル
CREATE TABLE IF NOT EXISTS room_availability_cache (
    id BIGSERIAL PRIMARY KEY,
    room_name VARCHAR(50) NOT NULL,
    target_date DATE NOT NULL,
    time_slot VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    checked_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_room_availability_cache UNIQUE (room_name, target_date, time_slot)
);

-- 段階的通知の重複防止テーブル
CREATE TABLE IF NOT EXISTS adjacent_room_notifications (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    remaining_count INT NOT NULL,
    notified_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_adjacent_room_notifications UNIQUE (session_id, remaining_count)
);
