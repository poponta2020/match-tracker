package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 伝助側で削除された試合（日付×試合番号）を検知し、削除候補として記録するサービス。
 *
 * <p>承認されるまでは {@link com.karuta.matchtracker.entity.PracticeParticipant} 等の
 * 既存データには一切触れない（{@link DensukeImportService} の抽選フェーズ処理とは独立した
 * 追加チェックとして実装し、既存の同期ロジックへの影響を避ける）。
 *
 * <p>totalMatches は変更せず、承認済みの削除候補は「欠番」として扱う
 * （{@link DensukeWriteService} のスケジュール生成側で除外する）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DensukeDeletionDetectionService {

    private final DensukeUrlRepository densukeUrlRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;
    private final LineNotificationService lineNotificationService;

    /**
     * @param scraped      同一サイクルで既に取得済みのスクレイピング結果（再取得しない）
     * @param targetMonth  同期対象の年月（月初日。{@link DensukeImportService#importFromDensuke} と同じ値）
     * @param organizationId 対象団体ID
     */
    @Transactional
    public void detectDeletions(DensukeScraper.DensukeData scraped, LocalDate targetMonth, Long organizationId) {
        DensukeUrl densukeUrl = densukeUrlRepository
                .findByYearAndMonthAndOrganizationId(targetMonth.getYear(), targetMonth.getMonthValue(), organizationId)
                .orElse(null);
        if (densukeUrl == null) {
            return;
        }
        Long urlId = densukeUrl.getId();

        Set<String> actualKeys = scraped.getEntries().stream()
                .filter(e -> e.getDate().getYear() == targetMonth.getYear()
                        && e.getDate().getMonthValue() == targetMonth.getMonthValue())
                .map(e -> key(e.getDate(), e.getMatchNumber()))
                .collect(Collectors.toSet());

        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonthAndOrganizationId(
                targetMonth.getYear(), targetMonth.getMonthValue(), organizationId);

        Map<String, DensukeDeletionCandidate> pendingByKey = densukeDeletionCandidateRepository
                .findByDensukeUrlIdAndStatus(urlId, DensukeDeletionCandidate.Status.PENDING).stream()
                .collect(Collectors.toMap(c -> key(c.getSessionDate(), c.getMatchNumber()), c -> c, (a, b) -> a));

        Set<String> approvedKeys = densukeDeletionCandidateRepository
                .findByDensukeUrlIdAndStatus(urlId, DensukeDeletionCandidate.Status.APPROVED).stream()
                .map(c -> key(c.getSessionDate(), c.getMatchNumber()))
                .collect(Collectors.toSet());

        // 却下済みの組も再検知の対象から除外する。除外しないと同じ (densuke_url_id, session_date,
        // match_number) を新規PENDINGとして再度saveしようとし、既存のREJECTED行とUNIQUE制約違反になる。
        Set<String> rejectedKeys = densukeDeletionCandidateRepository
                .findByDensukeUrlIdAndStatus(urlId, DensukeDeletionCandidate.Status.REJECTED).stream()
                .map(c -> key(c.getSessionDate(), c.getMatchNumber()))
                .collect(Collectors.toSet());

        List<String> newlyDetected = new ArrayList<>();

        for (PracticeSession session : sessions) {
            int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 0;
            for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                String k = key(session.getSessionDate(), matchNumber);
                if (approvedKeys.contains(k)) {
                    continue; // 承認済みの欠番は再検知しない
                }
                if (rejectedKeys.contains(k)) {
                    continue; // 却下済みは自動再オープンしない単純化方針
                }

                boolean presentOnDensuke = actualKeys.contains(k);
                DensukeDeletionCandidate pending = pendingByKey.get(k);

                if (!presentOnDensuke && pending == null) {
                    DensukeDeletionCandidate candidate = DensukeDeletionCandidate.builder()
                            .densukeUrlId(urlId)
                            .organizationId(organizationId)
                            .sessionDate(session.getSessionDate())
                            .matchNumber(matchNumber)
                            .status(DensukeDeletionCandidate.Status.PENDING)
                            .notifiedAt(JstDateTimeUtil.now())
                            .build();
                    densukeDeletionCandidateRepository.save(candidate);
                    newlyDetected.add(session.getSessionDate() + " 第" + matchNumber + "試合");
                    log.info("Densuke deletion candidate detected: urlId={}, date={}, matchNumber={}",
                            urlId, session.getSessionDate(), matchNumber);
                } else if (presentOnDensuke && pending != null) {
                    // 伝助側で行が復活した場合は未承認の削除候補を解消する（再オープンはしない単純化方針）
                    densukeDeletionCandidateRepository.delete(pending);
                    log.info("Densuke deletion candidate auto-resolved (row reappeared): urlId={}, date={}, matchNumber={}",
                            urlId, session.getSessionDate(), matchNumber);
                }
            }
        }

        if (!newlyDetected.isEmpty()) {
            lineNotificationService.sendDensukeDeletionCandidateDetectedNotification(organizationId, newlyDetected);
        }
    }

    private String key(LocalDate date, int matchNumber) {
        return date + "_" + matchNumber;
    }
}
