-- 選手データの必須フィールド更新スクリプト
-- 級（A-E）、かるた会（北大かるた会）、性別、利き手を設定

-- 既存の選手データを更新
UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'A級',
    `gender` = '男性',
    `dominant_hand` = '右'
WHERE `id` = 1 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'B級',
    `gender` = '女性',
    `dominant_hand` = '右'
WHERE `id` = 2 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'A級',
    `gender` = '男性',
    `dominant_hand` = '右'
WHERE `id` = 3 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'C級',
    `gender` = '女性',
    `dominant_hand` = '左'
WHERE `id` = 4 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'B級',
    `gender` = '男性',
    `dominant_hand` = '右'
WHERE `id` = 5 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'D級',
    `gender` = '女性',
    `dominant_hand` = '右'
WHERE `id` = 6 AND `karuta_club` IS NULL;

UPDATE `players` SET
    `karuta_club` = '北大かるた会',
    `kyu_rank` = 'E級',
    `gender` = '男性',
    `dominant_hand` = '右'
WHERE `id` = 7 AND `karuta_club` IS NULL;

-- ID=8の選手はすでにデータが入っているので、karuta_clubのみ更新
UPDATE `players` SET
    `karuta_club` = '北大かるた会'
WHERE `id` = 8 AND `karuta_club` IS NULL;

-- 更新後の確認クエリ
SELECT id, name, kyu_rank, karuta_club, gender, dominant_hand
FROM `players`
WHERE `deleted_at` IS NULL
ORDER BY id;
