package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.VenueCreateRequest;
import com.karuta.matchtracker.dto.VenueDto;
import com.karuta.matchtracker.dto.VenueMatchScheduleDto;
import com.karuta.matchtracker.dto.VenueUpdateRequest;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会場管理サービス
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository scheduleRepository;
    private final EntityManager entityManager;

    /**
     * 全会場を取得
     *
     * @return 会場リスト
     */
    @Transactional(readOnly = true)
    public List<VenueDto> getAllVenues() {
        List<Venue> venues = venueRepository.findAll();
        return venues.stream()
                .map(this::enrichVenueWithSchedules)
                .collect(Collectors.toList());
    }

    /**
     * 会場IDで会場を取得
     *
     * @param id 会場ID
     * @return 会場DTO
     */
    @Transactional(readOnly = true)
    public VenueDto getVenueById(Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", id));
        return enrichVenueWithSchedules(venue);
    }

    /**
     * 会場を新規作成
     *
     * @param request 会場作成リクエスト
     * @return 作成された会場DTO
     */
    @Transactional
    public VenueDto createVenue(VenueCreateRequest request) {
        // 会場名の重複チェック
        if (venueRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("会場名「" + request.getName() + "」は既に登録されています");
        }

        // 会場を保存
        Venue venue = Venue.builder()
                .name(request.getName())
                .defaultMatchCount(request.getDefaultMatchCount())
                .build();
        venue = venueRepository.save(venue);

        // 試合時間割を保存
        final Long venueId = venue.getId();
        List<VenueMatchSchedule> schedules = request.getSchedules().stream()
                .map(scheduleReq -> VenueMatchSchedule.builder()
                        .venueId(venueId)
                        .matchNumber(scheduleReq.getMatchNumber())
                        .startTime(scheduleReq.getStartTime())
                        .endTime(scheduleReq.getEndTime())
                        .build())
                .collect(Collectors.toList());
        scheduleRepository.saveAll(schedules);

        return enrichVenueWithSchedules(venue);
    }

    /**
     * 会場を更新
     *
     * @param id 会場ID
     * @param request 会場更新リクエスト
     * @return 更新された会場DTO
     */
    @Transactional
    public VenueDto updateVenue(Long id, VenueUpdateRequest request) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", id));

        // 会場名の重複チェック（自分以外）
        venueRepository.findByName(request.getName()).ifPresent(existingVenue -> {
            if (!existingVenue.getId().equals(id)) {
                throw new DuplicateResourceException("会場名「" + request.getName() + "」は既に登録されています");
            }
        });

        // 会場情報を更新
        venue.setName(request.getName());
        venue.setDefaultMatchCount(request.getDefaultMatchCount());
        venue = venueRepository.save(venue);

        // 既存の試合時間割を削除
        scheduleRepository.deleteByVenueId(id);

        // 削除をデータベースに即座に反映
        entityManager.flush();

        // 新しい試合時間割を保存
        final Long venueId = venue.getId();
        List<VenueMatchSchedule> schedules = request.getSchedules().stream()
                .map(scheduleReq -> VenueMatchSchedule.builder()
                        .venueId(venueId)
                        .matchNumber(scheduleReq.getMatchNumber())
                        .startTime(scheduleReq.getStartTime())
                        .endTime(scheduleReq.getEndTime())
                        .build())
                .collect(Collectors.toList());
        scheduleRepository.saveAll(schedules);

        return enrichVenueWithSchedules(venue);
    }

    /**
     * 会場を削除
     *
     * @param id 会場ID
     */
    @Transactional
    public void deleteVenue(Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", id));

        // 試合時間割を削除
        scheduleRepository.deleteByVenueId(id);

        // 削除をデータベースに即座に反映
        entityManager.flush();

        // 会場を削除
        venueRepository.delete(venue);
    }

    /**
     * 会場に試合時間割を付与
     *
     * @param venue 会場エンティティ
     * @return 試合時間割付き会場DTO
     */
    private VenueDto enrichVenueWithSchedules(Venue venue) {
        List<VenueMatchSchedule> schedules = scheduleRepository
                .findByVenueIdOrderByMatchNumberAsc(venue.getId());

        List<VenueMatchScheduleDto> scheduleDtos = schedules.stream()
                .map(VenueMatchScheduleDto::fromEntity)
                .collect(Collectors.toList());

        VenueDto dto = VenueDto.fromEntity(venue);
        dto.setSchedules(scheduleDtos);
        return dto;
    }
}
