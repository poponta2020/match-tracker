package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineBroadcastGroupCreateRequest;
import com.karuta.matchtracker.dto.LineBroadcastLogsDto;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineBroadcastSend;
import com.karuta.matchtracker.entity.LineBroadcastSend.BroadcastStatus;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.LineBroadcastSendRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LineBroadcastAdminService の単体テスト（AC-10: CRUD/割当/状況/ログ＋org スコープ制御・AC-2 の GROUP 転用）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineBroadcastAdminService テスト")
class LineBroadcastAdminServiceTest {

    @Mock private LineBroadcastGroupRepository lineBroadcastGroupRepository;
    @Mock private LineBroadcastSendRepository lineBroadcastSendRepository;
    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CardDivisionBroadcastService cardDivisionBroadcastService;

    @InjectMocks private LineBroadcastAdminService service;

    private static final Long GROUP_ID = 5L;
    private static final Long ORG = 2L;

    private LineBroadcastGroup group(Long org) {
        return LineBroadcastGroup.builder().id(GROUP_ID).organizationId(org).name("北大全体").enabled(true).build();
    }

    private LineChannel channel(Long id, ChannelType type, ChannelStatus status, Long broadcastGroupId) {
        return LineChannel.builder()
                .id(id).lineChannelId("line-" + id).channelSecret("s").channelAccessToken("t")
                .channelType(type).status(status).broadcastGroupId(broadcastGroupId).build();
    }

    @BeforeEach
    void setUp() {
        when(lineChannelRepository.findByBroadcastGroupId(anyLong())).thenReturn(List.of());
        when(organizationRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("listGroups: ADMIN は自団体のみ")
    void listGroupsAdminScoped() {
        when(lineBroadcastGroupRepository.findByOrganizationId(ORG)).thenReturn(List.of(group(ORG)));

        service.listGroups("ADMIN", ORG);

        verify(lineBroadcastGroupRepository).findByOrganizationId(ORG);
        verify(lineBroadcastGroupRepository, never()).findAll();
    }

    @Test
    @DisplayName("listGroups: 団体未確定の ADMIN は fail-closed（空・全団体を漏らさない）")
    void listGroupsAdminNullOrgFailsClosed() {
        var result = service.listGroups("ADMIN", null);

        assertThat(result).isEmpty();
        verify(lineBroadcastGroupRepository, never()).findAll();
        verify(lineBroadcastGroupRepository, never()).findByOrganizationId(any());
    }

    @Test
    @DisplayName("listGroups: SUPER_ADMIN は全団体")
    void listGroupsSuperAdminAll() {
        when(lineBroadcastGroupRepository.findAll()).thenReturn(List.of(group(ORG)));

        service.listGroups("SUPER_ADMIN", null);

        verify(lineBroadcastGroupRepository).findAll();
    }

    @Test
    @DisplayName("createGroup: 正常系")
    void createGroupOk() {
        LineBroadcastGroupCreateRequest req = new LineBroadcastGroupCreateRequest();
        req.setOrganizationId(ORG);
        req.setName("北大全体");
        req.setExpectedRecipientCount(70);
        when(lineBroadcastGroupRepository.findByOrganizationId(ORG)).thenReturn(List.of());
        when(lineBroadcastGroupRepository.save(any())).thenAnswer(inv -> {
            LineBroadcastGroup g = inv.getArgument(0);
            g.setId(GROUP_ID);
            return g;
        });

        var dto = service.createGroup("SUPER_ADMIN", null, req);

        assertThat(dto.getOrganizationId()).isEqualTo(ORG);
        assertThat(dto.getExpectedRecipientCount()).isEqualTo(70);
    }

    @Test
    @DisplayName("createGroup: ADMIN が他団体を作成 → Forbidden")
    void createGroupScopeReject() {
        LineBroadcastGroupCreateRequest req = new LineBroadcastGroupCreateRequest();
        req.setOrganizationId(999L);
        req.setName("x");

        assertThatThrownBy(() -> service.createGroup("ADMIN", ORG, req))
                .isInstanceOf(ForbiddenException.class);
        verify(lineBroadcastGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("createGroup: 同一団体に既存があれば拒否")
    void createGroupDuplicate() {
        LineBroadcastGroupCreateRequest req = new LineBroadcastGroupCreateRequest();
        req.setOrganizationId(ORG);
        req.setName("x");
        when(lineBroadcastGroupRepository.findByOrganizationId(ORG)).thenReturn(List.of(group(ORG)));

        assertThatThrownBy(() -> service.createGroup("SUPER_ADMIN", null, req))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("assignBot: 未使用PLAYERチャネルを GROUP に転用し broadcast_group_id を設定")
    void assignBotFlipsPlayerToGroup() {
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        LineChannel ch = channel(10L, ChannelType.PLAYER, ChannelStatus.AVAILABLE, null);
        when(lineChannelRepository.findById(10L)).thenReturn(Optional.of(ch));

        service.assignBot("SUPER_ADMIN", null, GROUP_ID, 10L);

        ArgumentCaptor<LineChannel> captor = ArgumentCaptor.forClass(LineChannel.class);
        verify(lineChannelRepository).save(captor.capture());
        assertThat(captor.getValue().getChannelType()).isEqualTo(ChannelType.GROUP);
        assertThat(captor.getValue().getBroadcastGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    @DisplayName("assignBot: 使用中(ASSIGNED)のPLAYERチャネルは転用不可")
    void assignBotRejectsInUsePlayer() {
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        when(lineChannelRepository.findById(10L))
                .thenReturn(Optional.of(channel(10L, ChannelType.PLAYER, ChannelStatus.ASSIGNED, null)));

        assertThatThrownBy(() -> service.assignBot("SUPER_ADMIN", null, GROUP_ID, 10L))
                .isInstanceOf(IllegalStateException.class);
        verify(lineChannelRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignBot: ADMIN が他団体グループを操作 → Forbidden")
    void assignBotScopeReject() {
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(999L)));

        assertThatThrownBy(() -> service.assignBot("ADMIN", ORG, GROUP_ID, 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("unassignBot: PLAYER に戻し broadcast_group_id / line_group_id をクリア")
    void unassignBotRestoresPlayer() {
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        LineChannel ch = channel(10L, ChannelType.GROUP, ChannelStatus.AVAILABLE, GROUP_ID);
        ch.setLineGroupId("G-1");
        when(lineChannelRepository.findById(10L)).thenReturn(Optional.of(ch));

        service.unassignBot("SUPER_ADMIN", null, GROUP_ID, 10L);

        ArgumentCaptor<LineChannel> captor = ArgumentCaptor.forClass(LineChannel.class);
        verify(lineChannelRepository).save(captor.capture());
        assertThat(captor.getValue().getChannelType()).isEqualTo(ChannelType.PLAYER);
        assertThat(captor.getValue().getBroadcastGroupId()).isNull();
        assertThat(captor.getValue().getLineGroupId()).isNull();
    }

    @Test
    @DisplayName("getLogs: SKIPPED があれば hasRecentSkip=true")
    void getLogsFlagsSkip() {
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        LineBroadcastSend skipped = LineBroadcastSend.builder()
                .id(1L).broadcastGroupId(GROUP_ID).sessionId(100L)
                .status(BroadcastStatus.SKIPPED).errorMessage("枯渇").build();
        when(lineBroadcastSendRepository.findTop100ByBroadcastGroupIdOrderBySentAtDesc(GROUP_ID))
                .thenReturn(List.of(skipped));

        LineBroadcastLogsDto dto = service.getLogs("SUPER_ADMIN", null, GROUP_ID);

        assertThat(dto.isHasRecentSkip()).isTrue();
        assertThat(dto.getLogs()).hasSize(1);
        assertThat(dto.getLogs().get(0).getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("getStatus: CardDivisionBroadcastService.getRotationStatus に委譲する")
    void getStatusDelegates() {
        LineBroadcastGroup g = group(ORG);
        when(lineBroadcastGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(g));
        when(cardDivisionBroadcastService.getRotationStatus(g))
                .thenReturn(new CardDivisionBroadcastService.RotationStatus(10L, 70, 2, List.of()));

        var dto = service.getStatus("SUPER_ADMIN", null, GROUP_ID);

        assertThat(dto.getNextBotChannelId()).isEqualTo(10L);
        assertThat(dto.getRemainingBroadcasts()).isEqualTo(2);
        assertThat(dto.isExhausted()).isFalse();
    }
}
