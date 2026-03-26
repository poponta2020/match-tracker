package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.ByeActivity;
import com.karuta.matchtracker.repository.ByeActivityRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ByeActivityService {

    private final ByeActivityRepository byeActivityRepository;
    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    public List<ByeActivityDto> getByDate(LocalDate date) {
        List<ByeActivity> activities = byeActivityRepository.findBySessionDateOrderByMatchNumber(date);
        Map<Long, String> playerNames = collectPlayerNames(activities);
        return activities.stream()
                .map(a -> ByeActivityDto.fromEntity(a, playerNames.getOrDefault(a.getPlayerId(), "不明")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ByeActivityDto> getByDateAndMatch(LocalDate date, Integer matchNumber) {
        List<ByeActivity> activities = byeActivityRepository.findBySessionDateAndMatchNumber(date, matchNumber);
        Map<Long, String> playerNames = collectPlayerNames(activities);
        return activities.stream()
                .map(a -> ByeActivityDto.fromEntity(a, playerNames.getOrDefault(a.getPlayerId(), "不明")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ByeActivityDto> getByPlayer(Long playerId, ActivityType type) {
        List<ByeActivity> activities;
        if (type != null) {
            activities = byeActivityRepository.findByPlayerIdAndActivityTypeOrderBySessionDateDesc(playerId, type);
        } else {
            activities = byeActivityRepository.findByPlayerIdOrderBySessionDateDesc(playerId);
        }
        Map<Long, String> playerNames = collectPlayerNames(activities);
        return activities.stream()
                .map(a -> ByeActivityDto.fromEntity(a, playerNames.getOrDefault(a.getPlayerId(), "不明")))
                .collect(Collectors.toList());
    }

    /**
     * 抜け番活動を作成（本人入力用）
     * playerIdとuserIdが一致する場合のみ作成を許可
     */
    @Transactional
    public ByeActivityDto create(ByeActivityCreateRequest request, Long userId) {
        // 権限チェック: 本人のみ（userId == playerId）
        if (!request.getPlayerId().equals(userId)) {
            throw new IllegalArgumentException("他のプレイヤーの抜け番活動は登録できません");
        }

        String freeText = request.getActivityType() == ActivityType.OTHER ? request.getFreeText() : null;

        // 既存レコードがあればupsert（同一試合で同一選手は1レコード）
        Optional<ByeActivity> existing = byeActivityRepository
                .findBySessionDateAndMatchNumberAndPlayerId(request.getSessionDate(), request.getMatchNumber(), request.getPlayerId());

        ByeActivity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setActivityType(request.getActivityType());
            entity.setFreeText(freeText);
            entity.setUpdatedBy(userId);
        } else {
            entity = ByeActivity.builder()
                    .sessionDate(request.getSessionDate())
                    .matchNumber(request.getMatchNumber())
                    .playerId(request.getPlayerId())
                    .activityType(request.getActivityType())
                    .freeText(freeText)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();
        }

        ByeActivity saved = byeActivityRepository.save(entity);
        log.info("抜け番活動記録作成: date={}, match={}, player={}, type={}",
                saved.getSessionDate(), saved.getMatchNumber(), saved.getPlayerId(), saved.getActivityType());

        Map<Long, String> playerNames = collectPlayerNames(List.of(saved));
        return ByeActivityDto.fromEntity(saved, playerNames.getOrDefault(saved.getPlayerId(), "不明"));
    }

    /**
     * 抜け番活動を一括作成（管理者用）
     * @Transactional により削除→再作成がアトミックに実行される
     */
    @Transactional
    public List<ByeActivityDto> createBatch(LocalDate date, Integer matchNumber, List<ByeActivityBatchItemRequest> items, Long userId) {
        // 既存の同日同試合番号のレコードを論理削除してから再作成
        byeActivityRepository.softDeleteBySessionDateAndMatchNumber(date, matchNumber);
        byeActivityRepository.flush(); // 論理削除をDBに反映してからINSERT

        List<ByeActivity> entities = items.stream()
                .map(item -> {
                    String freeText = item.getActivityType() == ActivityType.OTHER ? item.getFreeText() : null;
                    return ByeActivity.builder()
                            .sessionDate(date)
                            .matchNumber(matchNumber)
                            .playerId(item.getPlayerId())
                            .activityType(item.getActivityType())
                            .freeText(freeText)
                            .createdBy(userId)
                            .updatedBy(userId)
                            .build();
                })
                .collect(Collectors.toList());

        List<ByeActivity> saved = byeActivityRepository.saveAll(entities);
        log.info("抜け番活動記録一括作成: date={}, match={}, count={}", date, matchNumber, saved.size());

        Map<Long, String> playerNames = collectPlayerNames(saved);
        return saved.stream()
                .map(a -> ByeActivityDto.fromEntity(a, playerNames.getOrDefault(a.getPlayerId(), "不明")))
                .collect(Collectors.toList());
    }

    /**
     * 抜け番活動を更新
     * 本人またはADMIN+が更新可能（Controller側で権限制御）
     */
    @Transactional
    public ByeActivityDto update(Long id, ByeActivityUpdateRequest request, Long userId) {
        ByeActivity entity = byeActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("抜け番活動記録が見つかりません: id=" + id));

        entity.setActivityType(request.getActivityType());
        entity.setFreeText(request.getActivityType() == ActivityType.OTHER ? request.getFreeText() : null);
        entity.setUpdatedBy(userId);

        ByeActivity saved = byeActivityRepository.save(entity);
        log.info("抜け番活動記録更新: id={}, type={}", id, saved.getActivityType());

        Map<Long, String> playerNames = collectPlayerNames(List.of(saved));
        return ByeActivityDto.fromEntity(saved, playerNames.getOrDefault(saved.getPlayerId(), "不明"));
    }

    /**
     * 更新対象のplayerIdを返す（Controller側の権限チェック用）
     */
    @Transactional(readOnly = true)
    public Long getPlayerIdForActivity(Long id) {
        return byeActivityRepository.findById(id)
                .filter(a -> !a.isDeleted())
                .map(ByeActivity::getPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("抜け番活動記録が見つかりません: id=" + id));
    }

    @Transactional
    public void delete(Long id) {
        ByeActivity entity = byeActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("抜け番活動記録が見つかりません: id=" + id));
        entity.setDeletedAt(LocalDateTime.now());
        byeActivityRepository.save(entity);
        log.info("抜け番活動記録論理削除: id={}", id);
    }

    private Map<Long, String> collectPlayerNames(List<ByeActivity> activities) {
        List<Long> allIds = activities.stream()
                .map(ByeActivity::getPlayerId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> names = new HashMap<>();
        playerRepository.findAllById(allIds).forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }
}
