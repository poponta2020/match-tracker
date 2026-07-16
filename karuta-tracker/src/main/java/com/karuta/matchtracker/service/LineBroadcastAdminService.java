package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineBroadcastGroupCreateRequest;
import com.karuta.matchtracker.dto.LineBroadcastGroupDto;
import com.karuta.matchtracker.dto.LineBroadcastGroupUpdateRequest;
import com.karuta.matchtracker.dto.LineBroadcastLogsDto;
import com.karuta.matchtracker.dto.LineBroadcastSendDto;
import com.karuta.matchtracker.dto.LineBroadcastStatusDto;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineBroadcastSend.BroadcastStatus;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.LineBroadcastSendRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.util.AdminScopeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 全体LINE配信の管理サービス（配信グループ CRUD・bot 割当/解除・稼働状況/ログ）。
 * ADMIN は自団体のみ、SUPER_ADMIN は全団体を操作できる（{@link AdminScopeValidator}）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineBroadcastAdminService {

    private final LineBroadcastGroupRepository lineBroadcastGroupRepository;
    private final LineBroadcastSendRepository lineBroadcastSendRepository;
    private final LineChannelRepository lineChannelRepository;
    private final OrganizationRepository organizationRepository;
    private final CardDivisionBroadcastService cardDivisionBroadcastService;

    /** 配信グループ一覧（ADMIN は自団体のみ・SUPER_ADMIN は全団体）。 */
    @Transactional(readOnly = true)
    public List<LineBroadcastGroupDto> listGroups(String role, Long adminOrgId) {
        final List<LineBroadcastGroup> groups;
        if ("ADMIN".equals(role)) {
            // 団体未確定の ADMIN は fail-closed（全団体を漏らさない）
            if (adminOrgId == null) {
                return List.of();
            }
            groups = lineBroadcastGroupRepository.findByOrganizationId(adminOrgId);
        } else {
            groups = lineBroadcastGroupRepository.findAll();
        }
        return groups.stream().map(this::toDto).toList();
    }

    @Transactional
    public LineBroadcastGroupDto createGroup(String role, Long adminOrgId, LineBroadcastGroupCreateRequest request) {
        // ADMIN は自団体を強制（クライアント入力の organizationId は使わない＝既存の自団体スコープ強制パターンに揃える）。
        // SUPER_ADMIN のみ request.organizationId で対象団体を指定する。
        final Long targetOrgId;
        if ("ADMIN".equals(role)) {
            if (adminOrgId == null) {
                throw new ForbiddenException("団体が未確定のため配信グループを作成できません");
            }
            targetOrgId = adminOrgId;
        } else {
            targetOrgId = request.getOrganizationId();
            if (targetOrgId == null) {
                throw new IllegalArgumentException("organizationId は必須です");
            }
        }
        // 1団体1グループ（Non-goals: 複数グループは今回スコープ外。DB でも一意インデックスで担保）
        if (!lineBroadcastGroupRepository.findByOrganizationId(targetOrgId).isEmpty()) {
            throw new IllegalStateException("この団体の配信グループは既に存在します");
        }
        LineBroadcastGroup group = LineBroadcastGroup.builder()
                .organizationId(targetOrgId)
                .name(request.getName())
                .enabled(true)
                .expectedRecipientCount(request.getExpectedRecipientCount())
                .build();
        LineBroadcastGroup saved = lineBroadcastGroupRepository.save(group);
        log.info("Created broadcast group {} for org {}", saved.getId(), saved.getOrganizationId());
        return toDto(saved);
    }

    @Transactional
    public LineBroadcastGroupDto updateGroup(String role, Long adminOrgId, Long groupId,
                                             LineBroadcastGroupUpdateRequest request) {
        LineBroadcastGroup group = loadScoped(role, adminOrgId, groupId);
        if (request.getName() != null) {
            group.setName(request.getName());
        }
        if (request.getEnabled() != null) {
            group.setEnabled(request.getEnabled());
        }
        // 明示的な未設定化（clearフラグ）を「省略（更新しない）」と区別して扱う
        if (Boolean.TRUE.equals(request.getClearExpectedRecipientCount())) {
            group.setExpectedRecipientCount(null);
        } else if (request.getExpectedRecipientCount() != null) {
            group.setExpectedRecipientCount(request.getExpectedRecipientCount());
        }
        return toDto(lineBroadcastGroupRepository.save(group));
    }

    /** 未使用チャネルを GROUP に転用してこの配信グループに割り当てる。 */
    @Transactional
    public void assignBot(String role, Long adminOrgId, Long groupId, Long channelId) {
        loadScoped(role, adminOrgId, groupId);
        LineChannel channel = lineChannelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));

        // 無効化されたチャネルは割当で再有効化させない（DISABLED の意味を壊さない）
        if (channel.getStatus() == ChannelStatus.DISABLED) {
            throw new IllegalStateException("無効化されたチャネルは配信botに割り当てできません");
        }

        switch (channel.getChannelType()) {
            case PLAYER -> {
                if (channel.getStatus() != ChannelStatus.AVAILABLE) {
                    throw new IllegalStateException("使用中のPLAYERチャネルは配信botに転用できません");
                }
            }
            case GROUP -> {
                if (channel.getBroadcastGroupId() != null && !channel.getBroadcastGroupId().equals(groupId)) {
                    throw new IllegalStateException("このbotは既に別の配信グループに割り当てられています");
                }
            }
            case ADMIN -> throw new IllegalStateException("管理者用チャネルは配信botに転用できません");
        }

        channel.setChannelType(ChannelType.GROUP);
        channel.setBroadcastGroupId(groupId);
        channel.setStatus(ChannelStatus.AVAILABLE);
        lineChannelRepository.save(channel);
        log.info("Assigned channel {} to broadcast group {} as GROUP bot", channelId, groupId);
    }

    /**
     * bot の配信グループ割り当てを解除する。
     * 個人割当（PLAYER）プールには戻さず、未割当の GROUP/AVAILABLE として残す
     * （全体グループに招待済みだった bot が個人1:1通知チャネルに再利用されるのを防ぎ、AC-2 の分離境界を守る）。
     * グループID捕捉もクリアする。
     */
    @Transactional
    public void unassignBot(String role, Long adminOrgId, Long groupId, Long channelId) {
        loadScoped(role, adminOrgId, groupId);
        LineChannel channel = lineChannelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));
        if (!groupId.equals(channel.getBroadcastGroupId())) {
            throw new IllegalStateException("このbotはこの配信グループに割り当てられていません");
        }
        // channel_type は GROUP のまま（PLAYER プールへ戻さない）。割当と捕捉グループIDのみ解除
        channel.setBroadcastGroupId(null);
        channel.setLineGroupId(null);
        channel.setStatus(ChannelStatus.AVAILABLE);
        lineChannelRepository.save(channel);
        log.info("Unassigned channel {} from broadcast group {} (kept as unassigned GROUP)", channelId, groupId);
    }

    /** ローテーション稼働状況（次配信bot・各bot残枠・当月残り可能回数・枯渇アラート）。 */
    @Transactional(readOnly = true)
    public LineBroadcastStatusDto getStatus(String role, Long adminOrgId, Long groupId) {
        LineBroadcastGroup group = loadScoped(role, adminOrgId, groupId);
        return LineBroadcastStatusDto.fromRotationStatus(cardDivisionBroadcastService.getRotationStatus(group));
    }

    /** 配信ログ一覧＋枯渇アラート状態（AC-9）。 */
    @Transactional(readOnly = true)
    public LineBroadcastLogsDto getLogs(String role, Long adminOrgId, Long groupId) {
        loadScoped(role, adminOrgId, groupId);
        List<LineBroadcastSendDto> logs = lineBroadcastSendRepository
                .findTop100ByBroadcastGroupIdOrderBySentAtDesc(groupId).stream()
                .map(LineBroadcastSendDto::fromEntity)
                .toList();
        boolean hasRecentSkip = logs.stream().anyMatch(l -> BroadcastStatus.SKIPPED.name().equals(l.getStatus()));
        return LineBroadcastLogsDto.builder().logs(logs).hasRecentSkip(hasRecentSkip).build();
    }

    // ===== helpers =====

    private LineBroadcastGroup loadScoped(String role, Long adminOrgId, Long groupId) {
        LineBroadcastGroup group = lineBroadcastGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("LineBroadcastGroup", groupId));
        AdminScopeValidator.validateScope(role, adminOrgId, group.getOrganizationId(),
                "他団体の配信グループは操作できません");
        return group;
    }

    private LineBroadcastGroupDto toDto(LineBroadcastGroup group) {
        List<LineChannel> bots = lineChannelRepository.findByBroadcastGroupId(group.getId());
        int readyBotCount = (int) bots.stream()
                .filter(c -> c.getStatus() != ChannelStatus.DISABLED)
                .filter(c -> c.getLineGroupId() != null && !c.getLineGroupId().isBlank())
                .count();
        String orgName = organizationRepository.findById(group.getOrganizationId())
                .map(o -> o.getName()).orElse(null);
        return LineBroadcastGroupDto.fromEntity(group, orgName, bots.size(), readyBotCount);
    }
}
