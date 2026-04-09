package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.AdjacentRoomConfig;
import com.karuta.matchtracker.dto.AdjacentRoomStatusDto;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.RoomAvailabilityCache;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.RoomAvailabilityCacheRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 隣室空き確認・会場拡張サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdjacentRoomService {

    private final RoomAvailabilityCacheRepository roomAvailabilityCacheRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueRepository venueRepository;

    private static final String TIME_SLOT_EVENING = "evening";

    /**
     * 隣室の空き状況を取得する
     *
     * @param venueId 現在の会場ID
     * @param date 対象日付
     * @return 隣室の空き状況DTO（かでる和室でない場合はnull）
     */
    @Transactional(readOnly = true)
    public AdjacentRoomStatusDto getAdjacentRoomAvailability(Long venueId, LocalDate date) {
        if (!AdjacentRoomConfig.isKaderuRoom(venueId)) {
            return null;
        }

        String adjacentRoomName = AdjacentRoomConfig.getAdjacentRoomName(venueId);
        String status = "不明";

        // DBキャッシュから隣室の空き状況を取得
        var cache = roomAvailabilityCacheRepository
                .findByRoomNameAndTargetDateAndTimeSlot(adjacentRoomName, date, TIME_SLOT_EVENING);
        if (cache.isPresent()) {
            status = cache.get().getStatus();
        }

        return AdjacentRoomStatusDto.builder()
                .adjacentRoomName(adjacentRoomName)
                .status(status)
                .available("○".equals(status))
                .expandedVenueId(AdjacentRoomConfig.getExpandedVenueId(venueId))
                .expandedVenueName(AdjacentRoomConfig.getExpandedVenueName(venueId))
                .expandedCapacity(AdjacentRoomConfig.getExpandedCapacity(venueId))
                .build();
    }

    /**
     * 会場を拡張する（隣室と合わせた大部屋に変更）
     *
     * @param sessionId セッションID
     */
    @Transactional
    public void expandVenue(Long sessionId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        Long currentVenueId = session.getVenueId();
        if (!AdjacentRoomConfig.isKaderuRoom(currentVenueId)) {
            throw new IllegalStateException("この会場は拡張できません");
        }

        // 隣室の空き状況をサーバー側で再検証
        AdjacentRoomStatusDto adjacentRoom = getAdjacentRoomAvailability(currentVenueId, session.getSessionDate());
        if (adjacentRoom == null || !adjacentRoom.getAvailable()) {
            throw new IllegalStateException("隣室が空いていないため、会場を拡張できません");
        }

        Long expandedVenueId = AdjacentRoomConfig.getExpandedVenueId(currentVenueId);
        Integer expandedCapacity = AdjacentRoomConfig.getExpandedCapacity(currentVenueId);

        // 拡張後の会場が存在するか確認
        venueRepository.findById(expandedVenueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", expandedVenueId));

        session.setVenueId(expandedVenueId);
        session.setCapacity(expandedCapacity);
        practiceSessionRepository.save(session);

        log.info("Expanded venue for session {}: venueId {} -> {}, capacity -> {}",
                sessionId, currentVenueId, expandedVenueId, expandedCapacity);
    }
}
