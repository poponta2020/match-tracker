-- densuke_member_mappings に (densuke_url_id, densuke_member_id) のユニーク制約を追加
-- 同一伝助メンバーIDが複数のプレイヤーにマッピングされることを防止する
ALTER TABLE densuke_member_mappings
  ADD CONSTRAINT uq_densuke_member_mappings_url_member
  UNIQUE (densuke_url_id, densuke_member_id);
