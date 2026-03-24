package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.HomeDto;
import com.karuta.matchtracker.dto.NextParticipationDto;
import com.karuta.matchtracker.dto.ParticipationRateDto;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.service.PracticeParticipantService;
import com.karuta.matchtracker.service.PracticeSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ホーム画面用統合APIコントローラ
 * 1リクエストで全データを返し、フロントのラウンドトリップを削減する
 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class HomeController {

    private final PracticeSessionService practiceSessionService;
    private final PracticeParticipantService practiceParticipantService;
    private final NotificationRepository notificationRepository;
    private final PracticeParticipantRepository participantRepository;

    @GetMapping
    public ResponseEntity<HomeDto> getHomeData(@RequestParam Long playerId) {
        log.debug("GET /api/home?playerId={}", playerId);

        // 次の参加予定練習（なければnull）
        NextParticipationDto nextPractice = practiceSessionService.findNextParticipation(playerId);

        // 参加率TOP3 + 自分の参加率
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        List<ParticipationRateDto> top3 = practiceParticipantService.getParticipationRateTop3(year, month);

        // 自分の参加率: TOP3に含まれていればそこから取得、なければ個別に計算
        ParticipationRateDto myRate = top3.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseGet(() -> practiceParticipantService.getPlayerParticipationRate(playerId, year, month));

        // 未読通知数
        long unreadCount = notificationRepository.countByPlayerIdAndIsReadFalse(playerId);

        // 未応答のオファーがあるか
        boolean hasPendingOffer = participantRepository
                .existsByPlayerIdAndStatus(playerId, ParticipantStatus.OFFERED);

        HomeDto dto = HomeDto.builder()
                .nextPractice(nextPractice)
                .participationTop3(top3)
                .myParticipationRate(myRate)
                .unreadNotificationCount(unreadCount)
                .hasPendingOffer(hasPendingOffer)
                .build();

        return ResponseEntity.ok(dto);
    }
}
