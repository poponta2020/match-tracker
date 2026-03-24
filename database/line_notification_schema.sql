-- ============================================
-- LINE通知機能 データベーススキーマ
-- ============================================
-- 作成日: 2026-03-24
-- 対象テーブル: line_channels, line_channel_assignments,
--              line_notification_preferences, line_notification_schedule_settings,
--              line_message_log
-- ============================================

USE karuta_tracker;

-- ============================================
-- 1. line_channels（LINEチャネル管理）
-- ============================================
CREATE TABLE line_channels (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_name VARCHAR(100) NULL COMMENT '管理用表示名',
  line_channel_id VARCHAR(50) NOT NULL COMMENT 'LINE発行のチャネルID',
  channel_secret VARCHAR(255) NOT NULL COMMENT 'チャネルシークレット（AES-256-GCM暗号化）',
  channel_access_token TEXT NOT NULL COMMENT 'アクセストークン（AES-256-GCM暗号化）',
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE / ASSIGNED / LINKED / DISABLED',
  friend_add_url TEXT NULL COMMENT '友だち追加URL',
  qr_code_url TEXT NULL COMMENT 'QRコード画像URL',
  monthly_message_count INT NOT NULL DEFAULT 0 COMMENT '当月送信数',
  message_count_reset_at TIMESTAMP NULL COMMENT '送信数リセット日時',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  INDEX idx_line_channel_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LINEチャネル管理';

-- ============================================
-- 2. line_channel_assignments（チャネル割り当て）
-- ============================================
CREATE TABLE line_channel_assignments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  line_channel_id BIGINT NOT NULL COMMENT 'LINEチャネルID',
  player_id BIGINT NOT NULL COMMENT 'プレイヤーID',
  line_user_id VARCHAR(50) NULL COMMENT 'LINE userId（follow時に取得）',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / LINKED / UNLINKED / RECLAIMED',
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '割り当て日時',
  linked_at TIMESTAMP NULL COMMENT 'follow完了（LINKED化）日時',
  unlinked_at TIMESTAMP NULL COMMENT '解除日時',
  reclaim_warned_at TIMESTAMP NULL COMMENT '回収警告通知日時',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',

  FOREIGN KEY (line_channel_id) REFERENCES line_channels(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  INDEX idx_line_assignment_player (player_id),
  INDEX idx_line_assignment_channel (line_channel_id),
  INDEX idx_line_assignment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LINEチャネル割り当て';

-- ============================================
-- 3. line_notification_preferences（通知設定）
-- ============================================
CREATE TABLE line_notification_preferences (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL COMMENT 'プレイヤーID',
  lottery_result BOOLEAN NOT NULL DEFAULT TRUE COMMENT '抽選結果通知',
  waitlist_offer BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'キャンセル待ち連絡通知',
  offer_expired BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'オファー期限切れ通知',
  match_pairing BOOLEAN NOT NULL DEFAULT TRUE COMMENT '対戦組み合わせ通知',
  practice_reminder BOOLEAN NOT NULL DEFAULT TRUE COMMENT '参加予定リマインダー通知',
  deadline_reminder BOOLEAN NOT NULL DEFAULT TRUE COMMENT '締め切りリマインダー通知',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  UNIQUE KEY uk_line_pref_player (player_id),
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LINE通知設定（ユーザーごと）';

-- ============================================
-- 4. line_notification_schedule_settings（スケジュール設定）
-- ============================================
CREATE TABLE line_notification_schedule_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notification_type VARCHAR(30) NOT NULL COMMENT '通知種別（PRACTICE_REMINDER / DEADLINE_REMINDER）',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '有効/無効',
  days_before VARCHAR(50) NOT NULL COMMENT '送信日数（JSON配列。例: "[3, 1]"）',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
  updated_by BIGINT NULL COMMENT '最終更新者のplayer_id',

  UNIQUE KEY uk_schedule_type (notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='スケジュール型LINE通知設定';

-- デフォルトスケジュール設定
INSERT INTO line_notification_schedule_settings (notification_type, enabled, days_before, updated_at)
VALUES
  ('PRACTICE_REMINDER', TRUE, '[1]', NOW()),
  ('DEADLINE_REMINDER', TRUE, '[3, 1]', NOW());

-- ============================================
-- 5. line_message_log（送信ログ）
-- ============================================
CREATE TABLE line_message_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  line_channel_id BIGINT NOT NULL COMMENT 'LINEチャネルID',
  player_id BIGINT NOT NULL COMMENT 'プレイヤーID',
  notification_type VARCHAR(30) NOT NULL COMMENT '通知種別',
  message_content TEXT NOT NULL COMMENT '送信メッセージ内容',
  status VARCHAR(20) NOT NULL COMMENT 'SUCCESS / FAILED',
  error_message TEXT NULL COMMENT '失敗時のエラー内容',
  reference_id BIGINT NULL COMMENT '関連先ID（セッションIDや参加者IDなど）',
  sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '送信日時',

  FOREIGN KEY (line_channel_id) REFERENCES line_channels(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  INDEX idx_line_log_channel (line_channel_id),
  INDEX idx_line_log_player (player_id),
  INDEX idx_line_log_type_sent (notification_type, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LINE送信ログ';
