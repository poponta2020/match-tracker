-- matches テーブルに指導試合フラグ is_lesson を追加し、score_difference を NULL 許容に変更する。
-- 関連機能: docs/features/match-coaching-status/
--
-- 指導試合（上級者が初心者に教えながら行う試合）は勝敗（winner_id）のみを持ち、
-- 取り札枚数差（score_difference）を記録しない（NULL）。通常の勝敗統計には従来どおり計上する。
--
-- 注意（本番 PostgreSQL の実態に基づく設計）:
--   要件定義では「score_difference の範囲 CHECK 制約を NULL 許容へ修正」とあったが、
--   本番 DB を information_schema / pg_constraint で確認したところ matches に CHECK 制約は
--   1件も存在しなかった（範囲チェックはアプリ層の @Min/@Max のみで担保されている）。
--   よって CHECK 制約の修正・再作成は行わない。新規に範囲 CHECK を追加すると既存データ
--   と整合しない恐れがあるため、あえて追加しない。
--   なお PostgreSQL の CHECK は式が NULL の行を許容する（FALSE のみ拒否）ため、
--   仮に範囲 CHECK が存在したとしても NULL の score_difference は拒否されない。

-- 1) 指導試合フラグ（既存行は通常試合扱いの FALSE）
ALTER TABLE matches ADD COLUMN IF NOT EXISTS is_lesson BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) 指導試合では枚数差を持たないため NULL 許容に変更
ALTER TABLE matches ALTER COLUMN score_difference DROP NOT NULL;

COMMENT ON COLUMN matches.is_lesson IS '指導試合フラグ（TRUE=上級者が初心者に教える指導試合。勝者=指導した側、敗者=指導された側。score_difference は NULL）';
