package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineBroadcastSend;
import com.karuta.matchtracker.entity.LineBroadcastSend.BroadcastStatus;
import com.karuta.matchtracker.repository.LineBroadcastSendRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全体LINE配信ログの原子的操作サービス。
 *
 * <p>個人通知の {@link LineMessageLogService} と同様、各操作を {@code REQUIRES_NEW} で独立コミットする。
 * これにより送信権確保（RESERVED insert）が LINE 送信より前にコミットされ、送信完了前にプロセスが落ちても
 * RESERVED が残留 → {@link #releaseStale} で回復 → 同一ウィンドウ内で再送、という crash-safe な流れになる。
 */
@Service
@RequiredArgsConstructor
public class LineBroadcastSendService {

    private final LineBroadcastSendRepository repository;
    private final LineChannelRepository lineChannelRepository;

    /**
     * 原子的に送信権を確保する（INSERT ... ON CONFLICT DO NOTHING）。
     * sent_at には呼び出し側の注入時刻 now を使う（RESERVED 解放の cutoff と同一の時刻基準に揃え、
     * クラッシュ回復の解放判定が確実に働くようにする）。
     * @return true=確保成功（このプロセスが送信する）、false=既に送信済み/送信中
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(Long groupId, Long sessionId, Long channelId, Integer recipientCount,
                              LocalDateTime now) {
        return repository.tryAcquireBroadcastRight(
                groupId, sessionId, channelId, recipientCount, now) > 0;
    }

    /** RESERVED → SUCCESS に確定する。@return 更新行数 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markSucceeded(Long groupId, Long sessionId) {
        return repository.markBroadcastSucceeded(groupId, sessionId);
    }

    /** RESERVED → FAILED に変更し次回リトライを可能にする。@return 更新行数 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markFailed(Long groupId, Long sessionId, String errorMessage) {
        return repository.markBroadcastFailed(groupId, sessionId, errorMessage);
    }

    /** 古い RESERVED を FAILED に解放する（クラッシュ回復）。@return 解放行数 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseStale(LocalDateTime cutoff) {
        return repository.releaseStaleBroadcastReservations(cutoff);
    }

    /** 送信成功した bot の当月消費を想定受信数分だけ即時加算する（AC-5）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementChannelMonthlyCount(Long channelId, int delta) {
        lineChannelRepository.incrementMonthlyMessageCount(channelId, delta);
    }

    /** 枯渇・未設定でスキップした事実を記録する（管理画面のアラート実体・AC-9）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSkipped(Long groupId, Long sessionId, String reason, LocalDateTime now) {
        repository.save(LineBroadcastSend.builder()
                .broadcastGroupId(groupId)
                .sessionId(sessionId)
                .lineChannelId(null)
                .recipientCount(0)
                .status(BroadcastStatus.SKIPPED)
                .errorMessage(reason)
                .sentAt(now)
                .build());
    }

    /** (グループ, セッション) に SUCCESS または RESERVED が既に存在するか（冪等の早期短絡） */
    @Transactional(readOnly = true)
    public boolean hasBlockingSend(Long groupId, Long sessionId) {
        return repository.existsByBroadcastGroupIdAndSessionIdAndStatusIn(
                groupId, sessionId, List.of(BroadcastStatus.SUCCESS, BroadcastStatus.RESERVED));
    }

    /** (グループ, セッション) に指定時刻以降の SKIPPED 記録があるか（SKIPPED ログの重複記録抑止） */
    @Transactional(readOnly = true)
    public boolean hasSkippedSince(Long groupId, Long sessionId, LocalDateTime since) {
        return repository.existsByBroadcastGroupIdAndSessionIdAndStatusAndSentAtGreaterThanEqual(
                groupId, sessionId, BroadcastStatus.SKIPPED, since);
    }
}
