-- densuke_urls に編集用シークレット (sd) カラムを追加
-- アプリから自動作成した伝助ページのみ値が入る。手動登録URLは NULL のまま。
-- 将来の編集・削除 API で必要になる可能性があるため保存する。
ALTER TABLE densuke_urls ADD COLUMN densuke_sd VARCHAR(32);
