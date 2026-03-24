package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.LineConfig;
import com.karuta.matchtracker.dto.LineChannelCreateRequest;
import com.karuta.matchtracker.dto.LineChannelDto;
import com.karuta.matchtracker.dto.LineStatusDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LINEチャネル管理サービス
 *
 * チャネルの登録・割り当て・解放・回収を管理する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineChannelService {

    private final LineChannelRepository lineChannelRepository;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    private final PlayerRepository playerRepository;
    private final LineConfig lineConfig;

    private static final List<LineAssignmentStatus> ACTIVE_STATUSES =
            List.of(LineAssignmentStatus.PENDING, LineAssignmentStatus.LINKED);

    // ========== チャネル管理（管理者向け） ==========

    /**
     * チャネル一覧を取得（管理者向け）
     */
    @Transactional(readOnly = true)
    public List<LineChannelDto> getAllChannels() {
        List<LineChannel> channels = lineChannelRepository.findAll();
        return channels.stream().map(channel -> {
            LineChannelDto dto = LineChannelDto.fromEntity(channel);
            // 割り当て情報を付与
            lineChannelAssignmentRepository
                    .findByLineChannelIdAndStatusIn(channel.getId(), ACTIVE_STATUSES)
                    .ifPresent(assignment -> {
                        dto.setAssignedPlayerId(assignment.getPlayerId());
                        playerRepository.findById(assignment.getPlayerId())
                                .ifPresent(player -> dto.setAssignedPlayerName(player.getName()));
                    });
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * チャネルを個別登録
     */
    @Transactional
    public LineChannelDto createChannel(LineChannelCreateRequest request) {
        String encryptionKey = lineConfig.getEncryptionKey();

        LineChannel channel = LineChannel.builder()
                .channelName(request.getChannelName())
                .lineChannelId(request.getLineChannelId())
                .channelSecret(encryptIfKeyPresent(request.getChannelSecret(), encryptionKey))
                .channelAccessToken(encryptIfKeyPresent(request.getChannelAccessToken(), encryptionKey))
                .friendAddUrl(request.getFriendAddUrl())
                .qrCodeUrl(request.getQrCodeUrl())
                .build();

        lineChannelRepository.save(channel);
        log.info("Created LINE channel: {} (ID: {})", channel.getChannelName(), channel.getId());
        return LineChannelDto.fromEntity(channel);
    }

    /**
     * チャネルを一括登録（CSVデータから）
     */
    @Transactional
    public int importChannels(List<LineChannelCreateRequest> requests) {
        int count = 0;
        for (LineChannelCreateRequest request : requests) {
            createChannel(request);
            count++;
        }
        log.info("Imported {} LINE channels", count);
        return count;
    }

    /**
     * チャネルを無効化
     */
    @Transactional
    public void disableChannel(Long channelId) {
        LineChannel channel = lineChannelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));
        channel.setStatus(LineChannelStatus.DISABLED);
        lineChannelRepository.save(channel);
        log.info("Disabled LINE channel: {}", channelId);
    }

    /**
     * チャネルを有効化（AVAILABLEに戻す）
     */
    @Transactional
    public void enableChannel(Long channelId) {
        LineChannel channel = lineChannelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelId));
        channel.setStatus(LineChannelStatus.AVAILABLE);
        lineChannelRepository.save(channel);
        log.info("Enabled LINE channel: {}", channelId);
    }

    /**
     * チャネルの強制割り当て解除
     */
    @Transactional
    public void forceReleaseChannel(Long channelId) {
        releaseChannel(channelId);
        log.info("Force released LINE channel: {}", channelId);
    }

    // ========== ユーザー向け ==========

    /**
     * LINE通知を有効化（チャネル割り当て）
     */
    @Transactional
    public LineStatusDto enableLineNotification(Long playerId) {
        // 既に割り当て済みでないかチェック
        Optional<LineChannelAssignment> existing =
                lineChannelAssignmentRepository.findByPlayerIdAndStatusIn(playerId, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            LineChannel channel = lineChannelRepository.findById(existing.get().getLineChannelId())
                    .orElseThrow(() -> new ResourceNotFoundException("LineChannel", existing.get().getLineChannelId()));
            return LineStatusDto.builder()
                    .enabled(true)
                    .linked(existing.get().getStatus() == LineAssignmentStatus.LINKED)
                    .friendAddUrl(channel.getFriendAddUrl())
                    .qrCodeUrl(channel.getQrCodeUrl())
                    .build();
        }

        // 利用可能なチャネルを取得
        LineChannel channel = lineChannelRepository
                .findFirstByStatusOrderByIdAsc(LineChannelStatus.AVAILABLE)
                .orElseThrow(() -> new IllegalStateException("利用可能なLINEチャネルがありません"));

        // チャネルをASSIGNEDに変更
        channel.setStatus(LineChannelStatus.ASSIGNED);
        lineChannelRepository.save(channel);

        // 割り当てレコードを作成
        LineChannelAssignment assignment = LineChannelAssignment.builder()
                .lineChannelId(channel.getId())
                .playerId(playerId)
                .build();
        lineChannelAssignmentRepository.save(assignment);

        // 通知設定をデフォルトで作成（なければ）
        if (lineNotificationPreferenceRepository.findByPlayerId(playerId).isEmpty()) {
            lineNotificationPreferenceRepository.save(
                    LineNotificationPreference.builder().playerId(playerId).build());
        }

        log.info("Assigned LINE channel {} to player {}", channel.getId(), playerId);

        return LineStatusDto.builder()
                .enabled(true)
                .linked(false)
                .friendAddUrl(channel.getFriendAddUrl())
                .qrCodeUrl(channel.getQrCodeUrl())
                .build();
    }

    /**
     * LINE通知を無効化（チャネル解放）
     */
    @Transactional
    public void disableLineNotification(Long playerId) {
        Optional<LineChannelAssignment> assignment =
                lineChannelAssignmentRepository.findByPlayerIdAndStatusIn(playerId, ACTIVE_STATUSES);
        if (assignment.isEmpty()) {
            return;
        }

        releaseChannel(assignment.get().getLineChannelId());
        log.info("Player {} disabled LINE notification", playerId);
    }

    /**
     * LINE連携状態を取得
     */
    @Transactional(readOnly = true)
    public LineStatusDto getLineStatus(Long playerId) {
        Optional<LineChannelAssignment> assignment =
                lineChannelAssignmentRepository.findByPlayerIdAndStatusIn(playerId, ACTIVE_STATUSES);

        if (assignment.isEmpty()) {
            return LineStatusDto.builder().enabled(false).linked(false).build();
        }

        LineChannel channel = lineChannelRepository.findById(assignment.get().getLineChannelId())
                .orElse(null);

        return LineStatusDto.builder()
                .enabled(true)
                .linked(assignment.get().getStatus() == LineAssignmentStatus.LINKED)
                .friendAddUrl(channel != null ? channel.getFriendAddUrl() : null)
                .qrCodeUrl(channel != null ? channel.getQrCodeUrl() : null)
                .build();
    }

    // ========== 内部メソッド ==========

    /**
     * チャネルアクセストークンを復号して返す
     */
    public String getDecryptedAccessToken(Long channelDbId) {
        LineChannel channel = lineChannelRepository.findById(channelDbId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelDbId));
        return decryptIfKeyPresent(channel.getChannelAccessToken(), lineConfig.getEncryptionKey());
    }

    /**
     * チャネルシークレットを復号して返す
     */
    public String getDecryptedChannelSecret(Long channelDbId) {
        LineChannel channel = lineChannelRepository.findById(channelDbId)
                .orElseThrow(() -> new ResourceNotFoundException("LineChannel", channelDbId));
        return decryptIfKeyPresent(channel.getChannelSecret(), lineConfig.getEncryptionKey());
    }

    /**
     * チャネルの月間送信数をインクリメント
     */
    @Transactional
    public void incrementMessageCount(Long channelDbId) {
        LineChannel channel = lineChannelRepository.findById(channelDbId).orElse(null);
        if (channel != null) {
            channel.setMonthlyMessageCount(channel.getMonthlyMessageCount() + 1);
            lineChannelRepository.save(channel);
        }
    }

    /**
     * 月間送信上限チェック
     */
    @Transactional(readOnly = true)
    public boolean isWithinMonthlyLimit(Long channelDbId) {
        LineChannel channel = lineChannelRepository.findById(channelDbId).orElse(null);
        if (channel == null) return false;
        return channel.getMonthlyMessageCount() < lineConfig.getMonthlyMessageLimit();
    }

    /**
     * チャネル割り当て解放の共通処理
     */
    private void releaseChannel(Long channelDbId) {
        // 割り当てを解除
        lineChannelAssignmentRepository
                .findByLineChannelIdAndStatusIn(channelDbId, ACTIVE_STATUSES)
                .ifPresent(assignment -> {
                    assignment.setStatus(LineAssignmentStatus.UNLINKED);
                    assignment.setLineUserId(null);
                    assignment.setUnlinkedAt(LocalDateTime.now());
                    lineChannelAssignmentRepository.save(assignment);
                });

        // チャネルをAVAILABLEに戻す
        lineChannelRepository.findById(channelDbId).ifPresent(channel -> {
            channel.setStatus(LineChannelStatus.AVAILABLE);
            lineChannelRepository.save(channel);
        });
    }

    private String encryptIfKeyPresent(String value, String key) {
        if (key == null || key.isEmpty()) return value;
        return EncryptionUtil.encrypt(value, key);
    }

    private String decryptIfKeyPresent(String value, String key) {
        if (key == null || key.isEmpty()) return value;
        return EncryptionUtil.decrypt(value, key);
    }
}
