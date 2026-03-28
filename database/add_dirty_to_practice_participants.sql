-- practice_participants テーブルに dirty カラムを追加
ALTER TABLE practice_participants
  ADD COLUMN dirty BOOLEAN NOT NULL DEFAULT TRUE;
