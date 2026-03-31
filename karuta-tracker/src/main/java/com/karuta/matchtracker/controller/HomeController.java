package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.HomeDto;
import com.karuta.matchtracker.dto.NextParticipationDto;
import com.karuta.matchtracker.dto.OrganizationDto;
import com.karuta.matchtracker.dto.ParticipationGroupDto;
import com.karuta.matchtracker.dto.ParticipationRateDto;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.OrganizationService;
import com.karuta.matchtracker.service.PracticeParticipantService;
import com.karuta.matchtracker.service.PracticeSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final NotificationService notificationService;
    private final PracticeParticipantRepository participantRepository;
    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<HomeDto> getHomeData(@RequestParam Long playerId) {
        log.debug("GET /api/home?playerId={}", playerId);

        // 次の参加予定練習（なければnull）
        NextParticipationDto nextPractice = practiceSessionService.findNextParticipation(playerId);

        // 参加率（団体別）
        LocalDate now = JstDateTimeUtil.today();
        int year = now.getYear();
        int month = now.getMonthValue();

        List<OrganizationDto> playerOrgs = organizationService.getPlayerOrganizations(playerId);
        List<ParticipationGroupDto> participationGroups = new ArrayList<>();

        if (playerOrgs.size() == 1) {
            // 1団体: その団体のみ（団体名はフロント側で非表示）
            OrganizationDto org = playerOrgs.get(0);
            participationGroups.add(buildParticipationGroup(playerId, year, month, org.getId(), org.getName()));
        } else if (playerOrgs.size() > 1) {
            // 複数団体: 全体合算 + 各団体
            List<Long> orgIds = playerOrgs.stream().map(OrganizationDto::getId).toList();
            participationGroups.add(buildParticipationGroupAll(playerId, year, month, orgIds));
            for (OrganizationDto org : playerOrgs) {
                participationGroups.add(buildParticipationGroup(playerId, year, month, org.getId(), org.getName()));
            }
        }

        // 未読通知数
        long unreadCount = notificationService.getUnreadCount(playerId);

        // 未応答のオファーがあるか
        boolean hasPendingOffer = participantRepository
                .existsByPlayerIdAndStatus(playerId, ParticipantStatus.OFFERED);

        HomeDto dto = HomeDto.builder()
                .nextPractice(nextPractice)
                .participationGroups(participationGroups)
                .unreadNotificationCount(unreadCount)
                .hasPendingOffer(hasPendingOffer)
                .build();

        return ResponseEntity.ok(dto);
    }

    private ParticipationGroupDto buildParticipationGroup(Long playerId, int year, int month, Long orgId, String orgName) {
        List<ParticipationRateDto> top3 = practiceParticipantService.getParticipationRateTop3(year, month, orgId);
        ParticipationRateDto myRate = top3.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseGet(() -> practiceParticipantService.getPlayerParticipationRate(playerId, year, month, orgId));
        return ParticipationGroupDto.builder()
                .organizationId(orgId)
                .organizationName(orgName)
                .top3(top3)
                .myRate(myRate)
                .build();
    }

    private ParticipationGroupDto buildParticipationGroupAll(Long playerId, int year, int month, List<Long> orgIds) {
        List<ParticipationRateDto> top3 = practiceParticipantService.getParticipationRateTop3(year, month, orgIds);
        ParticipationRateDto myRate = top3.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseGet(() -> practiceParticipantService.getPlayerParticipationRate(playerId, year, month, orgIds));
        return ParticipationGroupDto.builder()
                .organizationId(null)
                .organizationName("全体")
                .top3(top3)
                .myRate(myRate)
                .build();
    }
}
