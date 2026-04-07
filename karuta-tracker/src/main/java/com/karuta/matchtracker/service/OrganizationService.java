package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.OrganizationDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final PlayerRepository playerRepository;
    private final PushNotificationPreferenceRepository pushNotificationPreferenceRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;

    /**
     * 団体一覧を取得
     */
    @Transactional(readOnly = true)
    public List<OrganizationDto> getAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * ユーザーの参加団体一覧を取得
     */
    @Transactional(readOnly = true)
    public List<OrganizationDto> getPlayerOrganizations(Long playerId) {
        List<PlayerOrganization> playerOrgs = playerOrganizationRepository.findByPlayerId(playerId);
        List<Long> orgIds = playerOrgs.stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toList());
        return organizationRepository.findAllById(orgIds).stream()
                .map(OrganizationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * ユーザーの参加団体IDリストを取得
     */
    @Transactional(readOnly = true)
    public List<Long> getPlayerOrganizationIds(Long playerId) {
        return playerOrganizationRepository.findByPlayerId(playerId).stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toList());
    }

    /**
     * ユーザーの参加団体を更新（最低1つ必須）
     */
    @Transactional
    public List<OrganizationDto> updatePlayerOrganizations(Long playerId, List<Long> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            throw new IllegalArgumentException("参加する練習会を最低1つ選択してください");
        }

        // 指定された団体が全て存在するか確認
        List<Organization> organizations = organizationRepository.findAllById(organizationIds);
        if (organizations.size() != organizationIds.size()) {
            throw new ResourceNotFoundException("指定された団体が見つかりません");
        }

        // 既存の紐づけを取得
        List<PlayerOrganization> existing = playerOrganizationRepository.findByPlayerId(playerId);
        Set<Long> existingOrgIds = existing.stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toSet());

        // 追加が必要なもの
        for (Long orgId : organizationIds) {
            if (!existingOrgIds.contains(orgId)) {
                PlayerOrganization po = PlayerOrganization.builder()
                        .playerId(playerId)
                        .organizationId(orgId)
                        .build();
                playerOrganizationRepository.save(po);
                createDefaultNotificationPreferences(playerId, orgId);
            }
        }

        // 削除が必要なもの
        Set<Long> newOrgIds = Set.copyOf(organizationIds);
        for (PlayerOrganization po : existing) {
            if (!newOrgIds.contains(po.getOrganizationId())) {
                playerOrganizationRepository.delete(po);
            }
        }

        return getPlayerOrganizations(playerId);
    }

    /**
     * ADMINの団体紐づけを変更（SUPER_ADMINのみ）
     */
    @Transactional
    public void updateAdminOrganization(Long playerId, Long organizationId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("プレイヤーが見つかりません: " + playerId));

        if (player.getRole() != Player.Role.ADMIN) {
            throw new IllegalArgumentException("ADMINロールのプレイヤーのみ団体紐づけを設定できます");
        }

        if (organizationId != null) {
            organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("団体が見つかりません: " + organizationId));
        }

        player.setAdminOrganizationId(organizationId);
        playerRepository.save(player);
    }

    /**
     * 選手が指定団体に未所属であれば自動的に所属させる（通知設定も作成）
     */
    @Transactional
    public void ensurePlayerBelongsToOrganization(Long playerId, Long organizationId) {
        if (organizationId == null) {
            return;
        }
        if (playerOrganizationRepository.existsByPlayerIdAndOrganizationId(playerId, organizationId)) {
            return;
        }
        try {
            PlayerOrganization po = PlayerOrganization.builder()
                    .playerId(playerId)
                    .organizationId(organizationId)
                    .build();
            playerOrganizationRepository.save(po);
            createDefaultNotificationPreferences(playerId, organizationId);
            log.info("Auto-assigned player {} to organization {}", playerId, organizationId);
        } catch (DataIntegrityViolationException e) {
            // 同時リクエストで既に登録済みの場合は無視（冪等性保証）
            log.debug("Player {} already belongs to organization {} (concurrent insert)", playerId, organizationId);
        }
    }

    /**
     * 団体追加時にデフォルト全ONの通知設定レコードを作成
     */
    private void createDefaultNotificationPreferences(Long playerId, Long organizationId) {
        // Web Push通知設定
        if (pushNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(playerId, organizationId).isEmpty()) {
            pushNotificationPreferenceRepository.save(PushNotificationPreference.builder()
                    .playerId(playerId)
                    .organizationId(organizationId)
                    .enabled(false)
                    .build());
        }
        // LINE通知設定
        if (lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(playerId, organizationId).isEmpty()) {
            lineNotificationPreferenceRepository.save(LineNotificationPreference.builder()
                    .playerId(playerId)
                    .organizationId(organizationId)
                    .build());
        }
    }

    /**
     * IDから団体を取得
     */
    @Transactional(readOnly = true)
    public Organization getOrganizationById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("団体が見つかりません: " + id));
    }
}
