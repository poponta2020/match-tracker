package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService 自動所属テスト")
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PushNotificationPreferenceRepository pushNotificationPreferenceRepository;
    @Mock
    private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    @DisplayName("未所属の場合、団体と通知設定が追加される")
    void ensure_notMember_addsOrganizationAndNotificationPreferences() {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(false);
        when(playerOrganizationRepository.insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(pushNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());
        when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository).insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class));
        verify(pushNotificationPreferenceRepository).save(any());
        verify(lineNotificationPreferenceRepository).save(any());
    }

    @Test
    @DisplayName("既に所属済みの場合、何も追加しない")
    void ensure_alreadyMember_skips() {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(true);

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
        verify(pushNotificationPreferenceRepository, never()).save(any());
        verify(lineNotificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("organizationIdがnullの場合、何もしない")
    void ensure_nullOrgId_skips() {
        organizationService.ensurePlayerBelongsToOrganization(1L, null);

        verify(playerOrganizationRepository, never()).existsByPlayerIdAndOrganizationId(anyLong(), anyLong());
        verify(playerOrganizationRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("同時リクエストと競合しても例外にならない（ON CONFLICT で挿入0行）")
    void ensure_concurrentInsert_handledGracefully() {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(false);
        // 事前チェック通過後に別リクエストが先に登録 → ON CONFLICT DO NOTHING で挿入0行
        when(playerOrganizationRepository.insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(0);

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository).insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class));
        // 競合に負けた側は通知設定を作成しない（先行リクエスト側が作成済み）
        verify(pushNotificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateOrganizationsExist: 全て存在すれば例外を投げない")
    void validateOrganizationsExist_allExist_passes() {
        when(organizationRepository.findAllById(any()))
                .thenReturn(List.of(
                        Organization.builder().id(10L).build(),
                        Organization.builder().id(20L).build()));

        organizationService.validateOrganizationsExist(List.of(10L, 20L));

        verify(organizationRepository).findAllById(any());
    }

    @Test
    @DisplayName("validateOrganizationsExist: 存在しないIDがあれば ResourceNotFoundException")
    void validateOrganizationsExist_missing_throws() {
        when(organizationRepository.findAllById(any()))
                .thenReturn(List.of(Organization.builder().id(10L).build())); // 20L が存在しない

        assertThatThrownBy(() -> organizationService.validateOrganizationsExist(List.of(10L, 20L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("validateOrganizationsExist: 空なら検証せず例外も投げない")
    void validateOrganizationsExist_empty_skips() {
        organizationService.validateOrganizationsExist(List.of());

        verify(organizationRepository, never()).findAllById(any());
    }
}
