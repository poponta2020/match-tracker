-- ============================================
-- キャンセル理由カラム追加 + システム設定テーブル作成
-- ============================================

-- practice_participants テーブルにキャンセル関連カラムを追加
ALTER TABLE practice_participants
  ADD COLUMN cancel_reason VARCHAR(50) NULL COMMENT 'キャンセル理由コード',
  ADD COLUMN cancel_reason_detail TEXT NULL COMMENT 'キャンセル理由詳細（その他の場合）',
  ADD COLUMN cancelled_at TIMESTAMP NULL COMMENT 'キャンセル日時';

-- システム設定テーブル
CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL UNIQUE COMMENT '設定キー',
  setting_value VARCHAR(255) NOT NULL COMMENT '設定値',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
  updated_by BIGINT NULL COMMENT '更新者ID'
);

-- 初期データ: 締切日数（月初から何日前）
INSERT INTO system_settings (setting_key, setting_value)
VALUES ('lottery_deadline_days_before', '0')
ON CONFLICT (setting_key) DO NOTHING;
