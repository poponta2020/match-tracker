package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineChannelCreateRequest;
import com.karuta.matchtracker.dto.LineChannelDto;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineNotificationPreference;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LINEチャネル管理サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineChannelService {

    private final LineChannelRepository lineChannelRepository;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    private final PlayerRepository playerRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final LineNotificationService lineNotificationService;
    private final LineMessagingService lineMessagingService;

    /**
     * プレイヤーにチャネルを割り当てる（用途別）
     * @return 割り当てたチャネル
     */
    @Transactional
    public LineChannel assignChannel(Long playerId, ChannelType channelType) {
        // ADMINチャネルの場合はロールチェック
        if (channelType == ChannelType.ADMIN) {
            Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));
            if (player.getRole() == Player.Role.PLAYER) {
                throw new IllegalStateException("管理者用LINEは管理者のみ利用できます");
            }
        }

        // 該当用途で既に割り当て済みか確認
        Optional<LineChannelAssignment> existing = lineChannelAssignmentRepository
            .findByPlayerIdAndChannelTypeAndStatusIn(playerId, channelType, List.of(AssignmentStatus.PENDING, AssignmentStatus.LINKED));
        if (existing.isPresent()) {
            return lineChannelRepository.findById(existing.get().getLineChannelId())
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", existing.get().getLineChannelId()));
        }

        // 該当用途のAVAILABLEなチャネルを取得
        String channelLabel = channelType == ChannelType.ADMIN ? "管理者用" : "選手用";
        LineChannel channel = lineChannelRepository.findFirstByStatusAndChannelTypeOrderByIdAsc(ChannelStatus.AVAILABLE, channelType)
            .orElseThrow(() -> new IllegalStateException(channelLabel + "のLINEチャネルが不足しています"));

        // チャネルをASSIGNEDに変更
        channel.setStatus(ChannelStatus.ASSIGNED);
        lineChannelRepository.save(channel);

        // 割り当てレコード作成
        LineChannelAssignment assignment = LineChannelAssignment.builder()
            .lineChannelId(channel.getId())
            .playerId(playerId)
            .channelType(channelType)
            .status(AssignmentStatus.PENDING)
            .build();
        lineChannelAssignmentRepository.save(assignment);

        // 通知設定レコードが無ければ作成（デフォルト全ON、ユーザーの登録団体ごと）
        if (lineNotificationPreferenceRepository.findByPlayerId(playerId).isEmpty()) {
            List<com.karuta.matchtracker.entity.PlayerOrganization> playerOrgs =
                    playerOrganizationRepository.findByPlayerId(playerId);
            for (var po : playerOrgs) {
                lineNotificationPreferenceRepository.save(
                    LineNotificationPreference.builder()
                            .playerId(playerId)
                            .organizationId(po.getOrganizationId())
                            .build()
                );
            }
        }

        log.info("Assigned {} LINE channel {} to player {}", channelType, channel.getId(), playerId);
        return channel;
    }

    /**
     * プレイヤーのチャネル割り当てを解除する（用途別）
     */
    @Transactional
    public void releaseChannel(Long playerId, ChannelType channelType) {
        Optional<LineChannelAssignment> assignmentOpt = lineChannelAssignmentRepository
            .findByPlayerIdAndChannelTypeAndStatusIn(playerId, channelType, List.of(AssignmentStatus.PENDING, AssignmentStatus.LINKED));
        if (assignmentOpt.isEmpty()) {
            log.warn("No active {} LINE channel assignment found for player {}", channelType, playerId);
            return;
        }

        LineChannelAssignment assignment = assignmentOpt.get();
        assignment.setStatus(AssignmentStatus.UNLINKED);
        assignment.setUnlinkedAt(JstDateTimeUtil.now());
        assignment.setLineUserId(null);
        lineChannelAssignmentRepository.save(assignment);

        // チャネルをAVAILABLEに戻す
        LineChannel channel = lineChannelRepository.findById(assignment.getLineChannelId())
            .orElseThrow(() -> new ResourceNotFoundException("LineChannel", assignment.getLineChannelId()));
        channel.setStatus(ChannelStatus.AVAILABLE);
        lineChannelRepository.save(channel);

        log.info("Released {} LINE channel {} from player {}", channelType, channel.getId(), playerId);
    }

    /**
     * LINE連携を完了する（line_user_idを保存してLINKED状態にする）
     */
    @Transactional
    public void linkChannel(Long channelId, String lineUserId) {
        Optional<LineChannelAssignment> assignmentOpt = lineChannelAssignmentRepository.findActiveByChannelId(channelId);
        if (assignmentOpt.isEmpty()) {
            log.warn("No active assignment found for channel {}", channelId);
            return;
        }

        LineChannelAssignment assignment = assignmentOpt.get();
        assignment.setLineUserId(lineUserId);
        assignment.setStatus(AssignmentStatus.LINKED);
        assignment.setLinkedAt(JstDateTimeUtil.now());
        lineChannelAssignmentRepository.save(assignment);

        // チャネルステータスもLINKEDに
        LineChannel channel = lineChannelRepository.findById(channelId)
            .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));
        channel.setStatus(ChannelStatus.LINKED);
        lineChannelRepository.save(channel);

        log.info("Linked LINE channel {} with lineUserId {} for player {}",
            channelId, lineUserId, assignment.getPlayerId());
    }

    /**
     * チャネルに紐づくプレイヤーへ確定済み抽選結果を送信する
     */
    public void sendPendingLotteryResultsForChannel(Long channelId) {
        lineChannelAssignmentRepository.findActiveByChannelId(channelId)
            .ifPresent(assignment -> {
                try {
                    lineNotificationService.sendLotteryResultsForPlayer(assignment.getPlayerId());
                } catch (Exception e) {
                    log.error("Failed to send lottery results after LINE linking for player {}",
                        assignment.getPlayerId(), e);
                }
            });
    }

    /**
     * チャネル一覧を取得（管理者向け、用途別フィルタ対応）
     */
    @Transactional(readOnly = true)
    public List<LineChannelDto> getAllChannels(ChannelType channelType) {
        List<LineChannel> channels = channelType != null
            ? lineChannelRepository.findAllByChannelType(channelType)
            : lineChannelRepository.findAll();
        return channels.stream().map(channel -> {
            LineChannelDto dto = LineChannelDto.fromEntity(channel);
            // 割り当てユーザー情報を付加
            lineChannelAssignmentRepository.findActiveByChannelId(channel.getId())
                .ifPresent(assignment -> {
                    dto.setAssignedPlayerId(assignment.getPlayerId());
                    playerRepository.findById(assignment.getPlayerId())
                        .ifPresent(player -> dto.setAssignedPlayerName(player.getName()));
                });
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * チャネルを新規登録する
     */
    @Transactional
    public LineChannel createChannel(LineChannelCreateRequest request) {
        ChannelType type = request.getChannelType() != null ? request.getChannelType() : ChannelType.PLAYER;
        LineChannel channel = LineChannel.builder()
            .channelName(request.getChannelName())
            .lineChannelId(request.getLineChannelId())
            .channelSecret(request.getChannelSecret())
            .channelAccessToken(request.getChannelAccessToken())
            .basicId(request.getBasicId())
            .channelType(type)
            .build();
        return lineChannelRepository.save(channel);
    }

    /**
     * チャネルの有効化/無効化を切り替える
     */
    @Transactional
    public void toggleChannelStatus(Long channelId, boolean disable) {
        LineChannel channel = lineChannelRepository.findById(channelId)
            .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));

        if (disable) {
            channel.setStatus(ChannelStatus.DISABLED);
        } else {
            channel.setStatus(ChannelStatus.AVAILABLE);
        }
        lineChannelRepository.save(channel);
    }

    /**
     * チャネルの強制割り当て解除
     */
    @Transactional
    public void forceReleaseChannel(Long channelId) {
        lineChannelAssignmentRepository.findActiveByChannelId(channelId)
            .ifPresent(assignment -> {
                assignment.setStatus(AssignmentStatus.RECLAIMED);
                assignment.setUnlinkedAt(JstDateTimeUtil.now());
                lineChannelAssignmentRepository.save(assignment);
            });

        LineChannel channel = lineChannelRepository.findById(channelId)
            .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));
        channel.setStatus(ChannelStatus.AVAILABLE);
        lineChannelRepository.save(channel);

        log.info("Force released LINE channel {}", channelId);
    }

    /**
     * 全チャネルのWebhook URLをLINEチャネルIDベースに一括更新する
     * @param baseUrl アプリケーションのベースURL（例: https://example.com）
     * @return 成功/失敗の件数
     */
    public java.util.Map<String, Integer> migrateWebhookUrls(String baseUrl) {
        List<LineChannel> channels = lineChannelRepository.findAll();
        int success = 0;
        int failed = 0;

        for (LineChannel channel : channels) {
            String webhookUrl = baseUrl + "/api/line/webhook/" + channel.getLineChannelId();
            boolean result = lineMessagingService.updateWebhookUrl(
                channel.getChannelAccessToken(), webhookUrl);
            if (result) {
                success++;
                log.info("Webhook URL migrated for channel {} (lineChannelId={})",
                    channel.getId(), channel.getLineChannelId());
            } else {
                failed++;
                log.warn("Failed to migrate webhook URL for channel {} (lineChannelId={})",
                    channel.getId(), channel.getLineChannelId());
            }
        }

        return java.util.Map.of("success", success, "failed", failed);
    }
}
