#!/usr/bin/env python3
"""
大量のテストデータを投入するスクリプト
"""
import requests
import random
from datetime import datetime, timedelta

# API Base URL
API_BASE = "http://localhost:8080/api"

# 認証情報
AUTH_TOKEN = None

# 会場情報
VENUES = {
    "中央区民センター": {"start_time": "17:00", "end_time": "21:00", "max_matches": 3},
    "クラーク会館": {"start_time": "09:00", "end_time": "21:00", "max_matches": 7}
}

# 級のマッピング（数値化して比較しやすくする）
KYU_RANK_ORDER = {"A級": 5, "B級": 4, "C級": 3, "D級": 2, "E級": 1}

def login():
    """管理者としてログイン"""
    global AUTH_TOKEN

    login_data = {
        "username": "admin",
        "password": "pppppppp"
    }

    resp = requests.post(f"{API_BASE}/players/login", json=login_data)
    if resp.status_code == 200:
        result = resp.json()
        AUTH_TOKEN = result.get('token') or result.get('id')  # tokenまたはplayerIdを取得
        print(f"ログイン成功: {result.get('name')}")
        return result
    else:
        print(f"ログイン失敗: {resp.status_code}")
        return None

def get_headers():
    """認証ヘッダーを取得"""
    if AUTH_TOKEN:
        return {
            "Authorization": f"Bearer {AUTH_TOKEN}",
            "Content-Type": "application/json"
        }
    return {"Content-Type": "application/json"}

def clean_existing_data():
    """既存の練習日程、試合結果、参加登録を削除"""
    print("既存データをクリーンアップ中...")

    headers = get_headers()

    # 練習参加登録を削除
    sessions_resp = requests.get(f"{API_BASE}/practice-sessions", headers=headers)
    if sessions_resp.status_code == 200:
        sessions = sessions_resp.json()
        for session in sessions:
            # 参加登録を削除（APIがあれば）
            pass

    # 試合結果を削除（APIで一括削除できない場合は個別削除）
    matches_resp = requests.get(f"{API_BASE}/matches", headers=headers)
    if matches_resp.status_code == 200:
        matches = matches_resp.json()
        for match in matches:
            requests.delete(f"{API_BASE}/matches/{match['id']}", headers=headers)
            print(f"  削除: 試合ID {match['id']}")

    # 練習日程を削除
    if sessions_resp.status_code == 200:
        sessions = sessions_resp.json()
        for session in sessions:
            requests.delete(f"{API_BASE}/practice-sessions/{session['id']}", headers=headers)
            print(f"  削除: 練習日程ID {session['id']}")

    print("クリーンアップ完了\n")

def update_all_passwords():
    """全選手のパスワードを pppppppp に統一"""
    print("全選手のパスワードを統一中...")

    players_resp = requests.get(f"{API_BASE}/players")
    players = players_resp.json()

    for player in players:
        # パスワード更新API（存在する場合）
        # この部分は実際のAPIエンドポイントに合わせて調整が必要
        print(f"  更新: {player['name']} のパスワード")

    print("パスワード統一完了（注意: API経由でのパスワード更新が必要）\n")

def add_new_players():
    """新規選手を追加（既存の8人と同じ級分布で8人追加）"""
    print("新規選手を追加中...")

    # 級分布: A級3人、B級2人、C級1人、D級1人、E級1人
    new_players_ranks = ["A級", "A級", "A級", "B級", "B級", "C級", "D級", "E級"]

    genders = ["男性", "女性"]
    hands = ["右", "左"]
    clubs = ["東京かるた会", "大阪かるた会", "京都かるた会", "福岡かるた会"]

    for i, kyu_rank in enumerate(new_players_ranks, start=1):
        player_data = {
            "username": f"選手{i}",
            "password": "pppppppp",
            "name": f"選手{i}",
            "gender": random.choice(genders),
            "dominantHand": random.choice(hands),
            "danRank": "無段" if kyu_rank in ["D級", "E級"] else random.choice(["無段", "初段", "二段"]),
            "kyuRank": kyu_rank,
            "karutaClub": random.choice(clubs),
            "remarks": f"テストユーザー{i}",
            "role": "PLAYER"
        }

        resp = requests.post(f"{API_BASE}/players/register", json=player_data)
        if resp.status_code in [200, 201]:
            print(f"  追加: {player_data['name']} ({kyu_rank})")
        else:
            print(f"  エラー: {player_data['name']} - {resp.status_code} {resp.text}")

    print("新規選手追加完了\n")

def create_practice_sessions():
    """2/1〜3/31の練習日程を作成"""
    print("練習日程を作成中...")

    start_date = datetime(2026, 2, 1)
    end_date = datetime(2026, 3, 31)

    venue_names = list(VENUES.keys())
    current_date = start_date
    venue_index = 0

    session_ids = []

    while current_date <= end_date:
        venue_name = venue_names[venue_index % 2]
        venue_info = VENUES[venue_name]

        session_data = {
            "date": current_date.strftime("%Y-%m-%d"),
            "venueName": venue_name,
            "startTime": venue_info["start_time"],
            "endTime": venue_info["end_time"],
            "maxMatches": venue_info["max_matches"]
        }

        resp = requests.post(f"{API_BASE}/practice-sessions", json=session_data)
        if resp.status_code in [200, 201]:
            session_id = resp.json().get('id')
            session_ids.append((session_id, current_date, venue_name, venue_info["max_matches"]))
            print(f"  作成: {current_date.strftime('%Y-%m-%d')} - {venue_name}")
        else:
            print(f"  エラー: {current_date.strftime('%Y-%m-%d')} - {resp.status_code}")

        current_date += timedelta(days=1)
        venue_index += 1

    print(f"練習日程作成完了（{len(session_ids)}日分）\n")
    return session_ids

def create_match_results(session_ids, all_players):
    """2/1〜2/26の試合結果を作成"""
    print("試合結果を作成中...")

    # 2/26までのセッションのみ
    target_sessions = [s for s in session_ids if s[1] <= datetime(2026, 2, 26)]

    for session_id, date, venue_name, max_matches in target_sessions:
        # その日の最大試合数分の試合結果を作成
        for match_num in range(max_matches):
            # ランダムに2人を選ぶ
            player1, player2 = random.sample(all_players, 2)

            # 級の差を計算
            rank1 = KYU_RANK_ORDER.get(player1['kyuRank'], 0)
            rank2 = KYU_RANK_ORDER.get(player2['kyuRank'], 0)
            rank_diff = abs(rank1 - rank2)

            # 級が2つ以上離れている場合は上位級が必ず勝利
            if rank_diff >= 2:
                if rank1 > rank2:
                    winner_id = player1['id']
                else:
                    winner_id = player2['id']
            else:
                # ランダムに勝者を決定
                winner_id = random.choice([player1['id'], player2['id']])

            match_data = {
                "practiceSessionId": session_id,
                "player1Id": player1['id'],
                "player2Id": player2['id'],
                "winnerId": winner_id
            }

            resp = requests.post(f"{API_BASE}/matches", json=match_data)
            if resp.status_code in [200, 201]:
                winner_name = player1['name'] if winner_id == player1['id'] else player2['name']
                print(f"  作成: {date.strftime('%Y-%m-%d')} - {player1['name']} vs {player2['name']} → {winner_name}勝利")
            else:
                print(f"  エラー: {date.strftime('%Y-%m-%d')} - {resp.status_code}")

    print("試合結果作成完了\n")

def create_practice_participations(session_ids, all_players):
    """2/1〜2/26の練習参加登録を作成"""
    print("練習参加登録を作成中...")

    # 2/26までのセッションのみ
    target_sessions = [s for s in session_ids if s[1] <= datetime(2026, 2, 26)]

    for session_id, date, venue_name, max_matches in target_sessions:
        # 各練習に60-80%の選手が参加
        participation_rate = random.uniform(0.6, 0.8)
        num_participants = int(len(all_players) * participation_rate)
        participants = random.sample(all_players, num_participants)

        for player in participants:
            participation_data = {
                "practiceSessionId": session_id,
                "playerId": player['id'],
                "willAttend": True
            }

            resp = requests.post(f"{API_BASE}/practice-sessions/participations", json=participation_data)
            if resp.status_code in [200, 201]:
                pass  # 成功時は何も表示しない（大量なので）
            else:
                print(f"  エラー: {player['name']} - {date.strftime('%Y-%m-%d')} - {resp.status_code}")

        print(f"  登録: {date.strftime('%Y-%m-%d')} - {num_participants}人参加")

    print("練習参加登録完了\n")

def main():
    print("=" * 60)
    print("テストデータ投入開始")
    print("=" * 60 + "\n")

    # 1. 既存データのクリーンアップ
    clean_existing_data()

    # 2. パスワード統一（注意: 実際のAPI実装が必要）
    # update_all_passwords()
    print("注意: パスワード統一はデータベース直接操作が必要です\n")

    # 3. 新規選手追加
    add_new_players()

    # 4. 全選手を取得
    players_resp = requests.get(f"{API_BASE}/players")
    all_players = players_resp.json()
    print(f"現在の選手数: {len(all_players)}人\n")

    # 5. 練習日程作成
    session_ids = create_practice_sessions()

    # 6. 試合結果作成
    create_match_results(session_ids, all_players)

    # 7. 練習参加登録作成
    create_practice_participations(session_ids, all_players)

    print("=" * 60)
    print("テストデータ投入完了！")
    print("=" * 60)

if __name__ == "__main__":
    main()
