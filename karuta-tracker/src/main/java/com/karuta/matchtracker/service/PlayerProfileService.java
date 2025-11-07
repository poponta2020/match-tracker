package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PlayerProfileCreateRequest;
import com.karuta.matchtracker.dto.PlayerProfileDto;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerProfile;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerProfileRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 選手プロフィール管理サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlayerProfileService {

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerRepository playerRepository;

    /**
     * 選手の現在有効なプロフィールを取得
     */
    public Optional<PlayerProfileDto> findCurrentProfile(Long playerId) {
        log.debug("Finding current profile for player: {}", playerId);
        validatePlayerExists(playerId);

        return playerProfileRepository.findCurrentByPlayerId(playerId)
                .map(profile -> enrichProfileWithPlayerName(profile));
    }

    /**
     * 選手の特定日時点のプロフィールを取得
     */
    public Optional<PlayerProfileDto> findProfileAtDate(Long playerId, LocalDate date) {
        log.debug("Finding profile for player {} at date {}", playerId, date);
        validatePlayerExists(playerId);

        return playerProfileRepository.findByPlayerIdAndDate(playerId, date)
                .map(profile -> enrichProfileWithPlayerName(profile));
    }

    /**
     * 選手の全プロフィール履歴を取得（新しい順）
     */
    public List<PlayerProfileDto> findProfileHistory(Long playerId) {
        log.debug("Finding profile history for player: {}", playerId);
        validatePlayerExists(playerId);

        return playerProfileRepository.findAllByPlayerIdOrderByValidFromDesc(playerId)
                .stream()
                .map(profile -> enrichProfileWithPlayerName(profile))
                .collect(Collectors.toList());
    }

    /**
     * プロフィールを新規登録
     * 既存の有効なプロフィールがあれば、その有効期限を設定
     */
    @Transactional
    public PlayerProfileDto createProfile(PlayerProfileCreateRequest request) {
        log.info("Creating new profile for player: {}", request.getPlayerId());
        validatePlayerExists(request.getPlayerId());

        // 新しいプロフィールの有効開始日
        LocalDate newValidFrom = request.getValidFrom();

        // 既存の有効なプロフィールを取得
        Optional<PlayerProfile> currentProfile = playerProfileRepository
                .findCurrentByPlayerId(request.getPlayerId());

        // 既存プロフィールがあり、新しいプロフィールの開始日がその期間内の場合、
        // 既存プロフィールの有効期限を新しいプロフィールの開始日の前日に設定
        currentProfile.ifPresent(existing -> {
            if (existing.getValidFrom().isBefore(newValidFrom)) {
                existing.setValidTo(newValidFrom.minusDays(1));
                playerProfileRepository.save(existing);
                log.info("Updated valid_to of existing profile to {}", existing.getValidTo());
            }
        });

        // 新しいプロフィールを保存
        PlayerProfile profile = request.toEntity();
        PlayerProfile saved = playerProfileRepository.save(profile);

        log.info("Successfully created profile with id: {}", saved.getId());
        return enrichProfileWithPlayerName(saved);
    }

    /**
     * プロフィールの有効期限を設定
     */
    @Transactional
    public PlayerProfileDto setValidTo(Long profileId, LocalDate validTo) {
        log.info("Setting valid_to for profile id: {} to {}", profileId, validTo);

        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("PlayerProfile", profileId));

        // 有効期限が開始日以降であることを確認
        if (validTo != null && validTo.isBefore(profile.getValidFrom())) {
            throw new IllegalArgumentException("valid_to must be after or equal to valid_from");
        }

        profile.setValidTo(validTo);
        PlayerProfile updated = playerProfileRepository.save(profile);

        log.info("Successfully set valid_to for profile id: {}", profileId);
        return enrichProfileWithPlayerName(updated);
    }

    /**
     * プロフィールを削除
     */
    @Transactional
    public void deleteProfile(Long profileId) {
        log.info("Deleting profile with id: {}", profileId);

        if (!playerProfileRepository.existsById(profileId)) {
            throw new ResourceNotFoundException("PlayerProfile", profileId);
        }

        playerProfileRepository.deleteById(profileId);
        log.info("Successfully deleted profile with id: {}", profileId);
    }

    /**
     * 選手の存在確認
     */
    private void validatePlayerExists(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }
    }

    /**
     * プロフィールに選手名を設定
     */
    private PlayerProfileDto enrichProfileWithPlayerName(PlayerProfile profile) {
        PlayerProfileDto dto = PlayerProfileDto.fromEntity(profile);

        playerRepository.findById(profile.getPlayerId())
                .ifPresent(player -> dto.setPlayerName(player.getName()));

        return dto;
    }
}
