-- practice_participants テーブルの status CHECK制約を更新
-- WAITLIST_DECLINED を追加
-- Hibernate ddl-auto=update では既存のCHECK制約が自動更新されないため手動で対応

ALTER TABLE practice_participants DROP CONSTRAINT practice_participants_status_check;
ALTER TABLE practice_participants ADD CONSTRAINT practice_participants_status_check
  CHECK (status::text = ANY(ARRAY[
    'PENDING',
    'WON',
    'WAITLISTED',
    'OFFERED',
    'DECLINED',
    'CANCELLED',
    'WAITLIST_DECLINED'
  ]));
