package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineChannelService テスト")
class LineChannelServiceTest {

    @Mock
    private LineChannelRepository lineChannelRepository;
    @Mock
    private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock
    private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private LineNotificationService lineNotificationService;

    @InjectMocks
    private LineChannelService lineChannelService;

    private static final Long PLAYER_ID = 1L;

    @Nested
    @DisplayName("assignChannel")
    class AssignChannelTest {

        @Test
        @DisplayName("PLAYERチャネルを正常に割り当てできる")
        void shouldAssignPlayerChannel() {
            LineChannel channel = LineChannel.builder()
                .id(10L)
                .channelType(ChannelType.PLAYER)
                .status(ChannelStatus.AVAILABLE)
                .build();

            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());
            when(lineChannelRepository.findFirstByStatusAndChannelTypeOrderByIdAsc(
                ChannelStatus.AVAILABLE, ChannelType.PLAYER))
                .thenReturn(Optional.of(channel));
            when(lineChannelRepository.save(any())).thenReturn(channel);
            when(lineChannelAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineNotificationPreferenceRepository.findByPlayerId(PLAYER_ID)).thenReturn(List.of());
            when(playerOrganizationRepository.findByPlayerId(PLAYER_ID)).thenReturn(List.of());

            LineChannel result = lineChannelService.assignChannel(PLAYER_ID, ChannelType.PLAYER);

            assertThat(result.getId()).isEqualTo(10L);

            ArgumentCaptor<LineChannelAssignment> captor = ArgumentCaptor.forClass(LineChannelAssignment.class);
            verify(lineChannelAssignmentRepository).save(captor.capture());
            assertThat(captor.getValue().getChannelType()).isEqualTo(ChannelType.PLAYER);
        }

        @Test
        @DisplayName("ADMINチャネルはADMINロールのプレイヤーに割り当てできる")
        void shouldAssignAdminChannelToAdmin() {
            Player admin = Player.builder().id(PLAYER_ID).role(Role.ADMIN).build();
            LineChannel channel = LineChannel.builder()
                .id(20L)
                .channelType(ChannelType.ADMIN)
                .status(ChannelStatus.AVAILABLE)
                .build();

            when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(admin));
            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.ADMIN), any()))
                .thenReturn(Optional.empty());
            when(lineChannelRepository.findFirstByStatusAndChannelTypeOrderByIdAsc(
                ChannelStatus.AVAILABLE, ChannelType.ADMIN))
                .thenReturn(Optional.of(channel));
            when(lineChannelRepository.save(any())).thenReturn(channel);
            when(lineChannelAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineNotificationPreferenceRepository.findByPlayerId(PLAYER_ID)).thenReturn(List.of());
            when(playerOrganizationRepository.findByPlayerId(PLAYER_ID)).thenReturn(List.of());

            LineChannel result = lineChannelService.assignChannel(PLAYER_ID, ChannelType.ADMIN);

            assertThat(result.getId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("ADMINチャネルはPLAYERロールには割り当て拒否される")
        void shouldRejectAdminChannelForPlayerRole() {
            Player player = Player.builder().id(PLAYER_ID).role(Role.PLAYER).build();
            when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));

            assertThatThrownBy(() -> lineChannelService.assignChannel(PLAYER_ID, ChannelType.ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理者のみ");
        }

        @Test
        @DisplayName("該当用途のチャネルが空の場合はエラー")
        void shouldThrowWhenNoAvailableChannel() {
            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());
            when(lineChannelRepository.findFirstByStatusAndChannelTypeOrderByIdAsc(
                ChannelStatus.AVAILABLE, ChannelType.PLAYER))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> lineChannelService.assignChannel(PLAYER_ID, ChannelType.PLAYER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("チャネルが不足");
        }

        @Test
        @DisplayName("既に同一用途で割り当て済みの場合は既存チャネルを返す")
        void shouldReturnExistingChannelIfAlreadyAssigned() {
            LineChannelAssignment existing = LineChannelAssignment.builder()
                .lineChannelId(10L)
                .channelType(ChannelType.PLAYER)
                .status(AssignmentStatus.LINKED)
                .build();
            LineChannel channel = LineChannel.builder().id(10L).build();

            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.of(existing));
            when(lineChannelRepository.findById(10L)).thenReturn(Optional.of(channel));

            LineChannel result = lineChannelService.assignChannel(PLAYER_ID, ChannelType.PLAYER);

            assertThat(result.getId()).isEqualTo(10L);
            verify(lineChannelRepository, never()).findFirstByStatusAndChannelTypeOrderByIdAsc(any(), any());
        }
    }

    @Nested
    @DisplayName("releaseChannel")
    class ReleaseChannelTest {

        @Test
        @DisplayName("PLAYERチャネルを正常に解放できる")
        void shouldReleasePlayerChannel() {
            LineChannelAssignment assignment = LineChannelAssignment.builder()
                .id(1L)
                .lineChannelId(10L)
                .playerId(PLAYER_ID)
                .channelType(ChannelType.PLAYER)
                .status(AssignmentStatus.LINKED)
                .build();
            LineChannel channel = LineChannel.builder().id(10L).status(ChannelStatus.LINKED).build();

            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.of(assignment));
            when(lineChannelRepository.findById(10L)).thenReturn(Optional.of(channel));
            when(lineChannelAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(lineChannelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            lineChannelService.releaseChannel(PLAYER_ID, ChannelType.PLAYER);

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNLINKED);
            assertThat(channel.getStatus()).isEqualTo(ChannelStatus.AVAILABLE);
        }

        @Test
        @DisplayName("割り当てがない場合は何もしない")
        void shouldDoNothingWhenNoAssignment() {
            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(PLAYER_ID), eq(ChannelType.ADMIN), any()))
                .thenReturn(Optional.empty());

            lineChannelService.releaseChannel(PLAYER_ID, ChannelType.ADMIN);

            verify(lineChannelRepository, never()).save(any());
        }
    }
}
