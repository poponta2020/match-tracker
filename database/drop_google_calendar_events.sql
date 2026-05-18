-- iCalフィード方式への切り替えに伴い、旧Google Calendar OAuth方式の同期マッピングテーブルを削除
-- 対応Issue: #660（親Issue: #650）

DROP TABLE IF EXISTS google_calendar_events;
