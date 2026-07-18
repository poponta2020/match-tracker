-- 認証トークン表（auth-tokenization）
--   ヘッダー自己申告（X-User-Role / X-User-Id）による認証を廃し、サーバ発行・サーバ検証・
--   失効可能なトークンに置き換えるための土台。
--
--   token_hash には生トークンではなく SHA-256 hex（64文字）のみを保存する。
--   DB が漏洩しても保存値からトークンを復元・再利用できないようにするため。
--   （生トークンは発行時のレスポンスでのみクライアントへ渡る）
--
--   失効は revoked_at に時刻を入れる論理失効。行は削除しない。
--   player_id 単位の一括失効（パスワード変更・選手の論理削除）で使うため player_id に索引を張る。
--
--   players.password は既に VARCHAR(255) のため、BCrypt ハッシュ（60文字）の保存に
--   スキーマ変更は不要。平文からの変換は起動時の PasswordHashMigrationRunner が行う。
--
-- 本番 Render PostgreSQL への適用必須（entity 変更と同一 PR・CLAUDE.md 最重要ルール）。

--   player_id には外部キーを張る（players を参照する既存23制約と同じ方針）。
--   ON DELETE CASCADE は付けない。選手の削除は論理削除（deleted_at）が正で、
--   物理削除は制約違反として弾きたいため（万一 ID が再利用されると、古いトークンが
--   別人に解決されてしまう）。
-- 外部キーはインラインで書かない。インライン REFERENCES は PostgreSQL が
-- auth_tokens_player_id_fkey を自動生成するため、下の ALTER と合わせて
-- 新規環境では同じ制約が2本できてしまう。命名を1本に揃えるため ALTER 側に寄せる。
CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
);

-- 既に auth_tokens を作成済みの環境（本番に先行適用した場合）向けに、外部キーだけを後から張る。
-- CREATE TABLE IF NOT EXISTS では追加されないため。DROP → ADD で冪等にする
-- （PostgreSQL に ADD CONSTRAINT IF NOT EXISTS が無いため。既存 migration と同じ書き方）。
ALTER TABLE auth_tokens DROP CONSTRAINT IF EXISTS fk_auth_tokens_player;
ALTER TABLE auth_tokens ADD CONSTRAINT fk_auth_tokens_player
    FOREIGN KEY (player_id) REFERENCES players(id);

-- 選手単位の一括失効（パスワード変更・論理削除）用
CREATE INDEX IF NOT EXISTS idx_auth_tokens_player_id ON auth_tokens (player_id);

-- 期限切れレコードの掃除・棚卸し用
CREATE INDEX IF NOT EXISTS idx_auth_tokens_expires_at ON auth_tokens (expires_at);
