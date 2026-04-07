package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.PlayerOrganization;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

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
        when(pushNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());
        when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository).save(any(PlayerOrganization.class));
        verify(pushNotificationPreferenceRepository).save(any());
        verify(lineNotificationPreferenceRepository).save(any());
    }

    @Test
    @DisplayName("既に所属済みの場合、何も追加しない")
    void ensure_alreadyMember_skips() {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(true);

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository, never()).save(any());
        verify(pushNotificationPreferenceRepository, never()).save(any());
        verify(lineNotificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("organizationIdがnullの場合、何もしない")
    void ensure_nullOrgId_skips() {
        organizationService.ensurePlayerBelongsToOrganization(1L, null);

        verify(playerOrganizationRepository, never()).existsByPlayerIdAndOrganizationId(anyLong(), anyLong());
        verify(playerOrganizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("同時リクエストでユニーク制約違反が発生しても例外にならない")
    void ensure_concurrentInsert_handledGracefully() {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 2L)).thenReturn(false);
        when(playerOrganizationRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        organizationService.ensurePlayerBelongsToOrganization(1L, 2L);

        verify(playerOrganizationRepository).save(any(PlayerOrganization.class));
        // 例外は握り潰されるので、通知設定は作成されない（既に別リクエストで作成済み）
        verify(pushNotificationPreferenceRepository, never()).save(any());
    }
}
