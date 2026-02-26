#!/usr/bin/env python3
"""
PostgreSQLに直接接続してテストデータを投入するスクリプト
"""
import os
import random
from datetime import datetime, timedelta

try:
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    print("psycopg2がインストールされていません。")
    print("pip install psycopg2-binary を実行してください")
    exit(1)

# データベース接続情報
DB_HOST = "dpg-d6c3rgn5r7bs73an03pg-a.oregon-postgres.render.com"
DB_PORT = "5432"
DB_NAME = "karuta_tracker"
DB_USER = "karuta"
DB_PASSWORD = "tQMNFG3XxpSo4CpFWipGwhInXMo2uv5N"

# 会場情報
VENUES = [
    ("中央区民センター", "17:00:00", "21:00:00", 3),
    ("クラーク会館", "09:00:00", "21:00:00", 7)
]

# 級のマッピング
KYU_RANK_ORDER = {"A級": 5, "B級": 4, "C級": 3, "D級": 2, "E級": 1}

def get_connection():
    """データベース接続を取得"""
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD
    )

def clean_existing_data(conn):
    """既存データをクリーンアップ"""
    print("既存データをクリーンアップ中...")
    cursor = conn.cursor()

    cursor.execute("DELETE FROM practice_participations")
    cursor.execute("DELETE FROM match_results")
    cursor.execute("DELETE FROM practice_sessions")

    conn.commit()
    print(f"  削除完了: 練習参加登録、試合結果、練習日程\n")

def update_passwords(conn):
    """全選手のパスワードを統一"""
    print("全選手のパスワードを統一中...")
    cursor = conn.cursor()

    cursor.execute("UPDATE players SET password = 'pppppppp' WHERE deleted_at IS NULL")
    conn.commit()
    print(f"  更新完了: 全選手のパスワードをppppppppに統一\n")

def add_new_players(conn):
    """新規選手を追加"""
    print("新規選手を追加中...")
    cursor = conn.cursor()

    new_players = [
        ('選手1', 'pppppppp', '選手1', '男性', '右', '二段', 'A級', '東京かるた会', 'テストユーザー1', 'PLAYER'),
        ('選手2', 'pppppppp', '選手2', '女性', '右', '初段', 'A級', '大阪かるた会', 'テストユーザー2', 'PLAYER'),
        ('選手3', 'pppppppp', '選手3', '男性', '左', '無段', 'A級', '京都かるた会', 'テストユーザー3', 'PLAYER'),
        ('選手4', 'pppppppp', '選手4', '女性', '右', '初段', 'B級', '福岡かるた会', 'テストユーザー4', 'PLAYER'),
        ('選手5', 'pppppppp', '選手5', '男性', '右', '無段', 'B級', '東京かるた会', 'テストユーザー5', 'PLAYER'),
        ('選手6', 'pppppppp', '選手6', '女性', '左', '無段', 'C級', '大阪かるた会', 'テストユーザー6', 'PLAYER'),
        ('選手7', 'pppppppp', '選手7', '男性', '右', '無段', 'D級', '京都かるた会', 'テストユーザー7', 'PLAYER'),
        ('選手8', 'pppppppp', '選手8', '女性', '右', '無段', 'E級', '福岡かるた会', 'テストユーザー8', 'PLAYER'),
    ]

    for player in new_players:
        cursor.execute("""
            INSERT INTO players (username, password, name, gender, dominant_hand, dan_rank, kyu_rank, karuta_club, remarks, role, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, player)
        print(f"  追加: {player[2]} ({player[6]})")

    conn.commit()
    print("新規選手追加完了\n")

def create_practice_sessions(conn):
    """練習日程を作成"""
    print("練習日程を作成中...")
    cursor = conn.cursor()

    start_date = datetime(2026, 2, 1)
    end_date = datetime(2026, 3, 31)
    current_date = start_date
    venue_index = 0

    sessions = []
    while current_date <= end_date:
        venue_name, start_time, end_time, max_matches = VENUES[venue_index % 2]

        cursor.execute("""
            INSERT INTO practice_sessions (date, venue_name, start_time, end_time, max_matches, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
        """, (current_date.date(), venue_name, start_time, end_time, max_matches))

        session_id = cursor.fetchone()[0]
        sessions.append((session_id, current_date, venue_name, max_matches))

        current_date += timedelta(days=1)
        venue_index += 1

    conn.commit()
    print(f"  作成完了: {len(sessions)}日分の練習日程\n")
    return sessions

def get_all_players(conn):
    """全選手を取得"""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT id, name, kyu_rank
        FROM players
        WHERE deleted_at IS NULL
        ORDER BY id
    """)

    players = []
    for row in cursor.fetchall():
        players.append({
            'id': row[0],
            'name': row[1],
            'kyu_rank': row[2]
        })

    return players

def create_match_results(conn, sessions, players):
    """試合結果を作成"""
    print("試合結果を作成中...")
    cursor = conn.cursor()

    # 2/26までのセッションのみ
    target_sessions = [s for s in sessions if s[1] <= datetime(2026, 2, 26)]

    match_count = 0
    for session_id, date, venue_name, max_matches in target_sessions:
        for _ in range(max_matches):
            player1, player2 = random.sample(players, 2)

            # 級の差を計算
            rank1 = KYU_RANK_ORDER.get(player1['kyu_rank'], 0)
            rank2 = KYU_RANK_ORDER.get(player2['kyu_rank'], 0)
            rank_diff = abs(rank1 - rank2)

            # 級が2つ以上離れている場合は上位級が必ず勝利
            if rank_diff >= 2:
                winner_id = player1['id'] if rank1 > rank2 else player2['id']
            else:
                winner_id = random.choice([player1['id'], player2['id']])

            cursor.execute("""
                INSERT INTO match_results (practice_session_id, player1_id, player2_id, winner_id, created_at, updated_at)
                VALUES (%s, %s, %s, %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, (session_id, player1['id'], player2['id'], winner_id))

            match_count += 1

        print(f"  作成: {date.strftime('%Y-%m-%d')} - {venue_name} ({max_matches}試合)")

    conn.commit()
    print(f"試合結果作成完了（合計{match_count}試合）\n")

def create_practice_participations(conn, sessions, players):
    """練習参加登録を作成"""
    print("練習参加登録を作成中...")
    cursor = conn.cursor()

    # 2/26までのセッションのみ
    target_sessions = [s for s in sessions if s[1] <= datetime(2026, 2, 26)]

    total_participations = 0
    for session_id, date, venue_name, max_matches in target_sessions:
        # 各練習に60-80%の選手が参加
        participation_rate = random.uniform(0.6, 0.8)
        num_participants = int(len(players) * participation_rate)
        participants = random.sample(players, num_participants)

        for player in participants:
            cursor.execute("""
                INSERT INTO practice_participations (practice_session_id, player_id, will_attend, created_at, updated_at)
                VALUES (%s, %s, %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, (session_id, player['id'], True))
            total_participations += 1

        print(f"  登録: {date.strftime('%Y-%m-%d')} - {num_participants}人参加")

    conn.commit()
    print(f"練習参加登録完了（合計{total_participations}件）\n")

def main():
    print("=" * 60)
    print("テストデータ投入開始")
    print("=" * 60 + "\n")

    try:
        # データベース接続
        conn = get_connection()
        print("データベース接続成功\n")

        # 1. 既存データのクリーンアップ
        clean_existing_data(conn)

        # 2. パスワード統一
        update_passwords(conn)

        # 3. 新規選手追加
        add_new_players(conn)

        # 4. 全選手を取得
        players = get_all_players(conn)
        print(f"現在の選手数: {len(players)}人\n")

        # 5. 練習日程作成
        sessions = create_practice_sessions(conn)

        # 6. 試合結果作成
        create_match_results(conn, sessions, players)

        # 7. 練習参加登録作成
        create_practice_participations(conn, sessions, players)

        conn.close()

        print("=" * 60)
        print("テストデータ投入完了！")
        print("=" * 60)

    except Exception as e:
        print(f"エラーが発生しました: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
