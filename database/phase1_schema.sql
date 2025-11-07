-- ============================================
-- Phase 1: MVP データベーススキーマ
-- ============================================
-- 作成日: 2025-11-06
-- 対象テーブル: players, player_profiles, matches, practice_sessions
-- ============================================

-- データベース作成
CREATE DATABASE IF NOT EXISTS karuta_tracker
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE karuta_tracker;

-- ============================================
-- 1. players（選手マスタ）
-- ============================================
CREATE TABLE players (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE COMMENT '選手名（ログインに使用）',
  password VARCHAR(255) NOT NULL COMMENT 'パスワード（BCryptでハッシュ化）',
  gender ENUM('男性', '女性', 'その他') NOT NULL COMMENT '性別',
  dominant_hand ENUM('右', '左', '両') NOT NULL COMMENT '利き手',
  role ENUM('SUPER_ADMIN', 'ADMIN', 'PLAYER') NOT NULL DEFAULT 'PLAYER' COMMENT 'ロール（権限管理）',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '削除日時（論理削除）',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  INDEX idx_name_active (name, deleted_at) COMMENT 'ログイン時のクエリ最適化',
  INDEX idx_deleted_at (deleted_at) COMMENT '削除済み選手の除外クエリ最適化'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='選手マスタ';

-- ============================================
-- 2. player_profiles（選手情報履歴）
-- ============================================
CREATE TABLE player_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL COMMENT '選手ID',
  karuta_club VARCHAR(200) NOT NULL COMMENT '所属かるた会',
  grade ENUM('A', 'B', 'C', 'D', 'E') NOT NULL COMMENT '級',
  dan ENUM('無', '初', '二', '三', '四', '五', '六', '七', '八') NOT NULL COMMENT '段位',
  valid_from DATE NOT NULL COMMENT '有効開始日',
  valid_to DATE NULL DEFAULT NULL COMMENT '有効終了日（NULLなら現在有効）',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  INDEX idx_player_date (player_id, valid_from, valid_to) COMMENT '選手の有効期間検索の最適化',
  INDEX idx_valid_to (valid_to) COMMENT '現在有効なプロフィール検索の最適化'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='選手情報履歴（級・段位の変更履歴管理）';

-- ============================================
-- 3. practice_sessions（練習日情報）
-- ============================================
CREATE TABLE practice_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_date DATE NOT NULL UNIQUE COMMENT '練習日',
  total_matches INT NOT NULL COMMENT 'その日の予定試合数',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  INDEX idx_session_date (session_date) COMMENT '練習日検索の最適化'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='練習日情報';

-- ============================================
-- 4. matches（対戦結果）
-- ============================================
CREATE TABLE matches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  match_date DATE NOT NULL COMMENT '対戦日',
  match_number INT NOT NULL COMMENT 'その日の第何試合目か',
  player1_id BIGINT NOT NULL COMMENT '選手1（player1_id < player2_id を保証）',
  player2_id BIGINT NOT NULL COMMENT '選手2',
  winner_id BIGINT NOT NULL COMMENT '勝者',
  score_difference INT NOT NULL COMMENT '枚数差（1～50）',
  notes TEXT NULL COMMENT 'コメント',
  created_by BIGINT NOT NULL COMMENT '作成者',
  updated_by BIGINT NOT NULL COMMENT '最終更新者',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',

  FOREIGN KEY (player1_id) REFERENCES players(id) ON DELETE RESTRICT,
  FOREIGN KEY (player2_id) REFERENCES players(id) ON DELETE RESTRICT,
  FOREIGN KEY (winner_id) REFERENCES players(id) ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES players(id) ON DELETE RESTRICT,
  FOREIGN KEY (updated_by) REFERENCES players(id) ON DELETE RESTRICT,

  INDEX idx_matches_date (match_date) COMMENT '日付別試合一覧の最適化',
  INDEX idx_matches_date_player1 (match_date, player1_id) COMMENT '選手1の日付別対戦検索',
  INDEX idx_matches_date_player2 (match_date, player2_id) COMMENT '選手2の日付別対戦検索',
  INDEX idx_matches_winner (winner_id) COMMENT '勝者での検索最適化',
  INDEX idx_matches_date_match_number (match_date, match_number) COMMENT '日付と試合番号での検索最適化',

  CONSTRAINT chk_score_difference CHECK (score_difference >= 1 AND score_difference <= 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='対戦結果';

-- ============================================
-- 初期データ投入（テスト用）
-- ============================================

-- SUPER_ADMINユーザーを1名作成
-- パスワード: "admin123" をBCryptでハッシュ化した値
-- 注意: 本番環境では必ず変更してください
INSERT INTO players (name, password, gender, dominant_hand, role)
VALUES ('管理者', '$2a$10$eP0K8qXKfQ5xVXz3XJxhEO5QnZ3v/YDGzM7mBPY7V8VHfZxJzYz6O', '男性', '右', 'SUPER_ADMIN');

-- 管理者のプロフィール作成
INSERT INTO player_profiles (player_id, karuta_club, grade, dan, valid_from, valid_to)
VALUES (1, '管理用', 'A', '初', CURDATE(), NULL);

-- 練習日を2件作成（今日と明日）
INSERT INTO practice_sessions (session_date, total_matches)
VALUES
  (CURDATE(), 4),
  (DATE_ADD(CURDATE(), INTERVAL 1 DAY), 4);

-- ============================================
-- 確認用クエリ
-- ============================================

-- テーブル一覧確認
-- SHOW TABLES;

-- 各テーブルのレコード数確認
-- SELECT 'players' AS table_name, COUNT(*) AS count FROM players
-- UNION ALL
-- SELECT 'player_profiles', COUNT(*) FROM player_profiles
-- UNION ALL
-- SELECT 'practice_sessions', COUNT(*) FROM practice_sessions
-- UNION ALL
-- SELECT 'matches', COUNT(*) FROM matches;
