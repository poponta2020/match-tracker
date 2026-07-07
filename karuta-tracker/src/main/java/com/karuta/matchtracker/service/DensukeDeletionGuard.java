package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 伝助側で削除が承認された(欠番の) (団体, 練習日, 試合番号) かどうかを判定する共有ガード。
 *
 * <p>承認済み欠番は {@code PracticeSession.totalMatches} を変更せず維持するため、通常の
 * 試合番号範囲チェックだけでは検知できない。参加者を新規作成・再有効化するあらゆる経路
 * （{@link PracticeParticipantService}・{@link WaitlistPromotionService} 等）で、
 * このガードを通してから登録を許可する。
 */
@Component
@RequiredArgsConstructor
public class DensukeDeletionGuard {

    private final DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;

    public boolean isApprovedDeletion(Long organizationId, LocalDate sessionDate, Integer matchNumber) {
        return densukeDeletionCandidateRepository
                .findByOrganizationIdAndSessionDateAndStatus(
                        organizationId, sessionDate, DensukeDeletionCandidate.Status.APPROVED)
                .stream().anyMatch(c -> c.getMatchNumber().equals(matchNumber));
    }
}
