package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.Player.Gender;
import com.karuta.matchtracker.entity.Player.DominantHand;
import com.karuta.matchtracker.entity.Player.DanRank;
import com.karuta.matchtracker.entity.Player.KyuRank;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/seed")
public class DataSeedController {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PracticeParticipantRepository practiceParticipantRepository;

    @Autowired
    private VenueRepository venueRepository;

    private static final Map<KyuRank, Integer> KYU_RANK_ORDER = Map.of(
            KyuRank.A級, 5,
            KyuRank.B級, 4,
            KyuRank.C級, 3,
            KyuRank.D級, 2,
            KyuRank.E級, 1
    );

    @PostMapping("/all")
    public ResponseEntity<?> seedAllData() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 既存データのクリーンアップ
            practiceParticipantRepository.deleteAll();
            matchRepository.deleteAll();
            practiceSessionRepository.deleteAll();
            result.put("cleanup", "完了");

            // 2. パスワード統一
            List<Player> allPlayers = playerRepository.findAll();
            for (Player player : allPlayers) {
                player.setPassword("pppppppp");
            }
            playerRepository.saveAll(allPlayers);
            result.put("passwordUpdate", "全" + allPlayers.size() + "人のパスワードを統一");

            // 3. 新規選手追加（既存選手がいる場合はスキップ）
            List<Player> newPlayers = new ArrayList<>();
            String[] newPlayerNames = {"選手1", "選手2", "選手3", "選手4", "選手5", "選手6", "選手7", "選手8"};
            Gender[] genders = {Gender.男性, Gender.女性, Gender.男性, Gender.女性, Gender.男性, Gender.女性, Gender.男性, Gender.女性};
            DominantHand[] hands = {DominantHand.右, DominantHand.右, DominantHand.左, DominantHand.右, DominantHand.右, DominantHand.左, DominantHand.右, DominantHand.右};
            DanRank[] danRanks = {DanRank.弐段, DanRank.初段, DanRank.無段, DanRank.初段, DanRank.無段, DanRank.無段, DanRank.無段, DanRank.無段};
            KyuRank[] kyuRanks = {KyuRank.A級, KyuRank.A級, KyuRank.A級, KyuRank.B級, KyuRank.B級, KyuRank.C級, KyuRank.D級, KyuRank.E級};
            String[] clubs = {"東京かるた会", "大阪かるた会", "京都かるた会", "福岡かるた会", "東京かるた会", "大阪かるた会", "京都かるた会", "福岡かるた会"};

            for (int i = 0; i < newPlayerNames.length; i++) {
                if (playerRepository.findByName(newPlayerNames[i]).isEmpty()) {
                    newPlayers.add(createPlayer(newPlayerNames[i], genders[i], hands[i], danRanks[i], kyuRanks[i], clubs[i]));
                }
            }
            if (!newPlayers.isEmpty()) {
                playerRepository.saveAll(newPlayers);
            }
            result.put("newPlayers", newPlayers.size() + "人追加");

            // 4. 練習日程作成（2/1〜3/31）
            // 会場を確認・作成
            Venue chuoCenter = venueRepository.findByName("中央区民センター")
                    .orElseGet(() -> venueRepository.save(Venue.builder()
                            .name("中央区民センター")
                            .defaultMatchCount(3)
                            .build()));

            Venue clarkHall = venueRepository.findByName("クラーク会館")
                    .orElseGet(() -> venueRepository.save(Venue.builder()
                            .name("クラーク会館")
                            .defaultMatchCount(7)
                            .build()));

            List<PracticeSession> sessions = new ArrayList<>();
            LocalDate startDate = LocalDate.of(2026, 2, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 31);
            LocalDate currentDate = startDate;
            int venueIndex = 0;

            while (!currentDate.isAfter(endDate)) {
                PracticeSession session = new PracticeSession();
                session.setSessionDate(currentDate);
                session.setCreatedBy(1L);  // adminユーザー
                session.setUpdatedBy(1L);

                if (venueIndex % 2 == 0) {
                    session.setVenueId(chuoCenter.getId());
                    session.setTotalMatches(3);
                } else {
                    session.setVenueId(clarkHall.getId());
                    session.setTotalMatches(7);
                }

                sessions.add(session);
                currentDate = currentDate.plusDays(1);
                venueIndex++;
            }
            practiceSessionRepository.saveAll(sessions);
            result.put("practiceSessions", sessions.size() + "日分作成");

            // 5. 試合結果作成（2/1〜2/26）
            List<Match> matches = new ArrayList<>();
            List<Player> allPlayersNow = playerRepository.findAll();
            Random random = new Random();

            for (PracticeSession session : sessions) {
                if (session.getSessionDate().isAfter(LocalDate.of(2026, 2, 26))) {
                    continue;
                }

                for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
                    // ランダムに2人選ぶ
                    Collections.shuffle(allPlayersNow);
                    Player p1 = allPlayersNow.get(0);
                    Player p2 = allPlayersNow.get(1);

                    // player1_id < player2_id を保証
                    Long player1Id = Math.min(p1.getId(), p2.getId());
                    Long player2Id = Math.max(p1.getId(), p2.getId());

                    Player player1 = p1.getId().equals(player1Id) ? p1 : p2;
                    Player player2 = p1.getId().equals(player2Id) ? p1 : p2;

                    // 級の差を計算
                    int rank1 = KYU_RANK_ORDER.getOrDefault(player1.getKyuRank(), 0);
                    int rank2 = KYU_RANK_ORDER.getOrDefault(player2.getKyuRank(), 0);
                    int rankDiff = Math.abs(rank1 - rank2);

                    // 勝者を決定
                    Long winnerId;
                    if (rankDiff >= 2) {
                        winnerId = rank1 > rank2 ? player1.getId() : player2.getId();
                    } else {
                        winnerId = random.nextBoolean() ? player1.getId() : player2.getId();
                    }

                    Match match = Match.builder()
                            .matchDate(session.getSessionDate())
                            .matchNumber(matchNum)
                            .player1Id(player1Id)
                            .player2Id(player2Id)
                            .winnerId(winnerId)
                            .scoreDifference(random.nextInt(25) + 1)
                            .createdBy(1L)
                            .updatedBy(1L)
                            .build();

                    matches.add(match);
                }
            }
            matchRepository.saveAll(matches);
            result.put("matchResults", matches.size() + "試合作成");

            // 6. 練習参加登録作成（2/1〜2/26）
            List<PracticeParticipant> participants = new ArrayList<>();
            for (PracticeSession session : sessions) {
                if (session.getSessionDate().isAfter(LocalDate.of(2026, 2, 26))) {
                    continue;
                }

                // 60-80%の選手が参加
                double participationRate = 0.6 + (random.nextDouble() * 0.2);
                int numParticipants = (int) (allPlayersNow.size() * participationRate);

                Collections.shuffle(allPlayersNow);
                for (int i = 0; i < numParticipants && i < allPlayersNow.size(); i++) {
                    PracticeParticipant participant = PracticeParticipant.builder()
                            .sessionId(session.getId())
                            .playerId(allPlayersNow.get(i).getId())
                            .build();
                    participants.add(participant);
                }
            }
            practiceParticipantRepository.saveAll(participants);
            result.put("practiceParticipants", participants.size() + "件作成");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    private Player createPlayer(String name, Gender gender, DominantHand hand, DanRank danRank, KyuRank kyuRank, String club) {
        return Player.builder()
                .name(name)
                .password("pppppppp")
                .gender(gender)
                .dominantHand(hand)
                .danRank(danRank)
                .kyuRank(kyuRank)
                .karutaClub(club)
                .remarks("テストユーザー")
                .role(Role.PLAYER)
                .build();
    }
}
