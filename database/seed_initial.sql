-- =============================================================================
-- seed_initial.sql — 新規デプロイ用 初期データ投入テンプレート
-- =============================================================================
--
-- 【用途】
--   本アプリを別のかるた会で新規に運用する（fork して別 Render＋別DBで自前運用する）際、
--   空DB（database/schema.sql 適用済み）に「最初の団体」と「最初の管理者（SUPER_ADMIN）」を
--   投入して、鶏卵問題（管理者がいないと何も作れない／団体作成APIが無い）を解消する。
--   投入するのは最小2行のみ（organizations×1、players(role=SUPER_ADMIN)×1）。
--   会場・追加選手・所属付与は、起動後にこの SUPER_ADMIN が管理画面から作成できる。
--
-- 【カスタマイズ箇所（適用前に自会の値へ書き換える）】
--   1. organizations.code   … 団体を識別する英小文字コード（例: 'myclub'）。任意。
--   2. organizations.name   … 団体の正式名称（例: '○○かるた会'）。UIに表示される。
--   3. organizations.color  … 団体カラー（#RRGGBB。UIのラベル色。未定なら緑 '#22c55e' のままで可）。
--   4. players.name         … 最初の管理者のログイン名。ログインIDとして使う。
--   5. players.password     … 最初の管理者のパスワード。**平文で記入する**（下記「順序制約」で自動ハッシュ化される）。
--                             ※ このSQLはコミットしないか、コミットするなら適用後に必ず自分の秘密値へ変更すること。
--
-- 【順序制約（最重要 / これを誤るとログイン不能になる）】
--   password は平文で記入し、アプリ起動時の PasswordHashMigrationRunner が BCrypt へ一括変換する。
--   したがって seed の適用は次のいずれかで行うこと:
--     (A) アプリ起動「前」に seed を適用する（次回起動時に変換される）。
--     (B) 起動中のDBへ seed を後入れした場合は、**アプリを再起動する**（再起動時に変換される）。
--   起動中DBへ後入れして再起動しないと、平文のまま照合され（BCrypt.matches(平文, 平文格納)=false）
--   ログインできない。再起動さえすれば平文→BCrypt化されログインできるようになる。
--
-- 【適用コマンド例】
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/seed_initial.sql
--   （schema.sql を適用したのと同じ psql 接続で続けて実行する）
--
-- 【冪等性】
--   organizations.code / players.name の自然キーに ON CONFLICT DO NOTHING を付けているため、
--   誤って複数回適用しても重複エラーにならない（2回目以降は何も挿入しない）。
--   すでにアプリがハッシュ化した後に再適用しても、既存行は DO NOTHING で保持され上書きされない。
--
-- 【備考】
--   - id はハードコードせず採番（organizations=シーケンス / players=IDENTITY）に委ねる。
--   - require_password_change=true を立てる（初回ログイン後の変更を促す。FEの誘導のみでBE強制ではないため、
--     運用者は上記5で必ず自分の秘密値を入れること）。
--   - ical_feed_token は NOT NULL・UNIQUE のためランダム生成する（md5(random())）。
-- =============================================================================

-- 1. 最初の団体
INSERT INTO organizations (code, name, color, deadline_type, created_at, updated_at)
VALUES ('CHANGEME_ORG_CODE', 'CHANGEME 団体名', '#22c55e', 'SAME_DAY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 2. 最初の管理者（SUPER_ADMIN）。password は平文。起動時に BCrypt 化される（上記「順序制約」参照）。
INSERT INTO players (name, password, gender, dominant_hand, role, require_password_change, ical_feed_token, created_at, updated_at)
VALUES ('CHANGEME_ADMIN', 'CHANGEME_PASSWORD', 'その他', '右', 'SUPER_ADMIN', true, md5(random()::text), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
