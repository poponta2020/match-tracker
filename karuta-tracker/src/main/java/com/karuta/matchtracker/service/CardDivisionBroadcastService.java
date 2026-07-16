package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 札分けの全体LINE一斉配信の核サービス。
 *
 * <p>1つの配信グループ×1セッションを、残枠のある {@link ChannelType#GROUP} 種別 bot 1体で
 * 原子的に「一度だけ」グループ配信する。ローテーション（bot選択）・冪等（送信権確保）・
 * 枠会計の即時反映（AC-5）・枯渇スキップ（AC-9）・団体分離（AC-8）を担う。
 *
 * <p>札組テキストは個人通知と完全同一（{@link CardDivisionTextService#buildTextForSession}・AC-1）。
 * 生成アルゴリズムは一切変更しない。
 *
 * <p><b>dedupe 不変条件</b>: 1 セッション（＝特定の練習日。{@code practice_sessions} は
 * {@code (session_date, organization_id)} 一意で id は再利用されない）につき全体配信は<b>恒久的に一度きり</b>。
 * よって dedupe は {@code (broadcast_group_id, session_id)} で足り、日付・月粒度は不要（同一 id が翌月に
 * 再配信対象になることはない）。同日に同一団体が2セッションを持つ場合はセッションごとに1回ずつ配信する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardDivisionBroadcastService {

    private final CardDivisionTextService cardDivisionTextService;
    private final LineChannelRepository lineChannelRepository;
    private final LineMessagingService lineMessagingService;
    private final LineBroadcastSendService lineBroadcastSendService;

    /** LINE無料枠（1チャネル/月） */
    static final int MONTHLY_QUOTA = 200;

    /**
     * RESERVED 解放の猶予（分）。配信ウィンドウの最小幅（30分前トリガー＝最大30分）より
     * 短くすること（さもないとクラッシュ回復が同一ウィンドウ内に間に合わず永久未送信になる）。
     */
    static final int RESERVED_TIMEOUT_MINUTES = 10;

    /**
     * 1配信グループ×1セッションを配信する。ポーリングや再起動で複数回呼ばれても
     * 原子的送信権確保により1回に収束する。
     *
     * @param group   配信グループ（enabled 前提。呼び出し側スケジューラが絞る）
     * @param session 対象セッション（group と同一団体であること・AC-8）
     * @param now     現在時刻（RESERVED 解放の cutoff に使用。テストで注入）
     */
    public void processGroupBroadcast(LineBroadcastGroup group, PracticeSession session, LocalDateTime now) {
        Long groupId = group.getId();
        Long sessionId = session.getId();

        // 団体分離の防御（AC-8）: 呼び出し側の絞り込みミスでも他団体セッションは絶対に流さない
        if (!group.getOrganizationId().equals(session.getOrganizationId())) {
            log.error("Broadcast org mismatch: group {} (org {}) vs session {} (org {}) — skipped",
                    groupId, group.getOrganizationId(), sessionId, session.getOrganizationId());
            return;
        }

        // クラッシュ回復: タイムアウトした RESERVED を FAILED に解放（同一ウィンドウ内で再送可能に）
        lineBroadcastSendService.releaseStale(now.minusMinutes(RESERVED_TIMEOUT_MINUTES));

        // 冪等の早期短絡: 既に SUCCESS/RESERVED があれば送信済み/送信中 → 何もしない
        if (lineBroadcastSendService.hasBlockingSend(groupId, sessionId)) {
            return;
        }

        // 配信可能な bot 候補（この group・GROUP 種別・非DISABLED・グループID捕捉済み）
        List<LineChannel> candidates = lineChannelRepository
                .findByBroadcastGroupIdAndChannelType(groupId, ChannelType.GROUP).stream()
                .filter(c -> c.getStatus() != ChannelStatus.DISABLED)
                .filter(c -> c.getLineGroupId() != null && !c.getLineGroupId().isBlank())
                .toList();

        if (candidates.isEmpty()) {
            recordSkippedOnce(groupId, sessionId, now, "配信可能なbotがありません（未割当/グループID未捕捉）");
            return;
        }

        // 想定受信数を解決（設定値優先、無ければ実グループ人数API）
        int expected = resolveExpectedRecipientCount(group, candidates);
        if (expected <= 0) {
            recordSkippedOnce(groupId, sessionId, now, "想定受信数を特定できません（設定値なし＋人数取得APIも失敗）");
            return;
        }

        // ローテーション選択（残枠のある先頭＝使い切ってから次へ・決定論・AC-4）
        Optional<LineChannel> selected = selectBot(candidates, expected);
        if (selected.isEmpty()) {
            // 全bot枯渇（AC-9）: 送信せず SKIPPED 記録＋アラート（課金なし）
            recordSkippedOnce(groupId, sessionId, now,
                    "全botが枯渇（当月枠不足・想定受信数=" + expected + "）");
            log.warn("Card division broadcast SKIPPED (all bots exhausted): group={}, session={}", groupId, sessionId);
            return;
        }
        LineChannel bot = selected.get();

        // 原子的に送信権を確保（AC-6）。false なら並行/前回ポーリングが確保済み → 何もしない
        // sent_at には注入 now を渡し、releaseStale の cutoff と時刻基準を揃える
        if (!lineBroadcastSendService.tryAcquire(groupId, sessionId, bot.getId(), expected, now)) {
            return;
        }

        // 送信（AC-1: 個人通知と同一テキスト。1体のみ・AC-5）
        String text = cardDivisionTextService.buildTextForSession(session);
        boolean ok = lineMessagingService.sendPushMessage(
                bot.getChannelAccessToken(), bot.getLineGroupId(), text);

        if (ok) {
            lineBroadcastSendService.markSucceeded(groupId, sessionId);
            // 消費即時反映（AC-5）: 毎時同期が後で実測へ補正
            lineBroadcastSendService.incrementChannelMonthlyCount(bot.getId(), expected);
            log.info("Card division broadcast sent: group={}, session={}, bot={}, recipients={}",
                    groupId, sessionId, bot.getId(), expected);
        } else {
            lineBroadcastSendService.markFailed(groupId, sessionId, "LINE API送信失敗");
            log.warn("Card division broadcast send failed: group={}, session={}, bot={}",
                    groupId, sessionId, bot.getId());
        }
    }

    private void recordSkippedOnce(Long groupId, Long sessionId, LocalDateTime now, String reason) {
        // 同日に既に SKIPPED を記録済みならログ肥大を避けて再記録しない
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        if (!lineBroadcastSendService.hasSkippedSince(groupId, sessionId, dayStart)) {
            lineBroadcastSendService.recordSkipped(groupId, sessionId, reason, now);
        }
    }

    /**
     * 想定受信数を解決する。グループ設定値（{@code expectedRecipientCount}）が優先。
     * 無ければ捕捉済み bot の1体で LINE のグループ人数取得APIを叩く。どちらも不可なら -1。
     */
    int resolveExpectedRecipientCount(LineBroadcastGroup group, List<LineChannel> candidates) {
        if (group.getExpectedRecipientCount() != null && group.getExpectedRecipientCount() > 0) {
            return group.getExpectedRecipientCount();
        }
        for (LineChannel c : candidates) {
            int count = lineMessagingService.getGroupMemberCount(c.getChannelAccessToken(), c.getLineGroupId());
            if (count > 0) {
                return count;
            }
        }
        return -1;
    }

    /**
     * ローテーション選択（純関数・決定論・AC-4）。
     * 「{@code 当月送信数 + 想定受信数 ≤ 200}」を満たす bot のうち、当月送信数が最大の1体を返す
     * （＝今使っている bot を使い切ってから次へ）。同数は id 昇順で決定。全滅なら空。
     */
    static Optional<LineChannel> selectBot(List<LineChannel> candidates, int expectedRecipient) {
        return candidates.stream()
                .filter(c -> monthlyCount(c) + expectedRecipient <= MONTHLY_QUOTA)
                .max(Comparator.comparingInt(CardDivisionBroadcastService::monthlyCount)
                        .thenComparing(Comparator.comparingLong(LineChannel::getId).reversed()));
    }

    private static int monthlyCount(LineChannel c) {
        return c.getMonthlyMessageCount() != null ? c.getMonthlyMessageCount() : 0;
    }

    // ===== 管理画面（タスク6）が消費する読み取り =====

    /**
     * 配信グループのローテーション状況を返す（次配信bot・各bot残枠・当月残り可能回数）。
     * 想定受信数はグループ設定値または実グループ人数APIで解決する。
     */
    public RotationStatus getRotationStatus(LineBroadcastGroup group) {
        List<LineChannel> bots = lineChannelRepository
                .findByBroadcastGroupIdAndChannelType(group.getId(), ChannelType.GROUP);

        List<LineChannel> ready = bots.stream()
                .filter(c -> c.getStatus() != ChannelStatus.DISABLED)
                .filter(c -> c.getLineGroupId() != null && !c.getLineGroupId().isBlank())
                .toList();

        int expected = resolveExpectedRecipientCount(group, ready);

        List<BotQuota> quotas = bots.stream()
                .map(c -> new BotQuota(
                        c.getId(),
                        c.getLineChannelId(),
                        c.getChannelName(),
                        monthlyCount(c),
                        Math.max(0, MONTHLY_QUOTA - monthlyCount(c)), // 200超過時に負数を出さない（表示・運用判断のブレ防止）
                        c.getLineGroupId() != null && !c.getLineGroupId().isBlank(),
                        c.getStatus() != ChannelStatus.DISABLED))
                .toList();

        Long nextBotId = expected > 0
                ? selectBot(ready, expected).map(LineChannel::getId).orElse(null)
                : null;

        int remainingBroadcasts = 0;
        if (expected > 0) {
            for (LineChannel c : ready) {
                remainingBroadcasts += Math.max(0, (MONTHLY_QUOTA - monthlyCount(c)) / expected);
            }
        }

        return new RotationStatus(nextBotId, expected, remainingBroadcasts, quotas);
    }

    /** ローテーション状況（管理画面表示用） */
    public record RotationStatus(
            Long nextBotChannelId,
            int expectedRecipientCount,
            int remainingBroadcasts,
            List<BotQuota> bots) {
    }

    /** 各 bot の枠状況（管理画面表示用） */
    public record BotQuota(
            Long channelId,
            String lineChannelId,
            String channelName,
            int monthlyMessageCount,
            int remainingQuota,
            boolean groupIdCaptured,
            boolean enabled) {
    }
}
