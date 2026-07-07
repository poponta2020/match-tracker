package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.AdminScopeValidator;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 伝助削除候補の承認/却下を扱うサービス。
 *
 * <p>承認時は該当試合の出欠エントリ（{@link com.karuta.matchtracker.entity.PracticeParticipant}）
 * のみを削除する。{@code totalMatches} や既存の対戦結果（{@code Match}）には触れない
 * （欠番方式・出欠エントリのみ削除の方針）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DensukeDeletionCandidateService {

    private final DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;

    public List<DensukeDeletionCandidate> listPending(Long organizationId) {
        return densukeDeletionCandidateRepository.findByOrganizationIdAndStatusOrderByDetectedAtDesc(
                organizationId, DensukeDeletionCandidate.Status.PENDING);
    }

    /**
     * 削除候補IDから実際の所属団体を引き、ADMINロールが他団体の候補を操作できないことを検証する。
     * クライアント指定の organizationId は信用せず、候補自身が持つ organizationId を正とする
     * （{@code PracticeSessionService.checkAdminScope} と同じ考え方）。
     */
    public void checkAdminScope(Long candidateId, String role, Long adminOrganizationId) {
        if (!"ADMIN".equals(role)) return;

        DensukeDeletionCandidate candidate = densukeDeletionCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("DensukeDeletionCandidate", candidateId));

        AdminScopeValidator.validateScope(role, adminOrganizationId, candidate.getOrganizationId(),
                "他団体の削除候補は操作できません");
    }

    @Transactional
    public DensukeDeletionCandidate approve(Long candidateId, Long adminUserId) {
        DensukeDeletionCandidate candidate = getPendingOrThrow(candidateId);

        PracticeSession session = practiceSessionRepository
                .findBySessionDateAndOrganizationId(candidate.getSessionDate(), candidate.getOrganizationId())
                .orElse(null);
        if (session != null) {
            practiceParticipantRepository.deleteBySessionIdAndMatchNumber(session.getId(), candidate.getMatchNumber());
            log.info("Approved densuke deletion candidate id={}: removed participants for session={} match={}",
                    candidateId, session.getId(), candidate.getMatchNumber());
        } else {
            log.warn("Approved densuke deletion candidate id={} but no matching PracticeSession found "
                    + "(date={}, orgId={})", candidateId, candidate.getSessionDate(), candidate.getOrganizationId());
        }

        candidate.setStatus(DensukeDeletionCandidate.Status.APPROVED);
        candidate.setResolvedAt(JstDateTimeUtil.now());
        candidate.setResolvedBy(adminUserId);
        return densukeDeletionCandidateRepository.save(candidate);
    }

    @Transactional
    public DensukeDeletionCandidate reject(Long candidateId, Long adminUserId) {
        DensukeDeletionCandidate candidate = getPendingOrThrow(candidateId);
        candidate.setStatus(DensukeDeletionCandidate.Status.REJECTED);
        candidate.setResolvedAt(JstDateTimeUtil.now());
        candidate.setResolvedBy(adminUserId);
        log.info("Rejected densuke deletion candidate id={}", candidateId);
        return densukeDeletionCandidateRepository.save(candidate);
    }

    private DensukeDeletionCandidate getPendingOrThrow(Long candidateId) {
        DensukeDeletionCandidate candidate = densukeDeletionCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("DensukeDeletionCandidate", candidateId));
        if (candidate.getStatus() != DensukeDeletionCandidate.Status.PENDING) {
            throw new IllegalStateException("既に処理済みの削除候補です（現在の状態: " + candidate.getStatus() + "）");
        }
        return candidate;
    }
}
