package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeDeletionDetectionService 単体テスト")
class DensukeDeletionDetectionServiceTest {

    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private DensukeDeletionDetectionService service;

    private static final LocalDate TARGET_MONTH = LocalDate.of(2026, 7, 1);
    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 7, 10);

    private DensukeUrl url() {
        return DensukeUrl.builder().id(10L).year(2026).month(7).organizationId(1L)
                .url("https://densuke.biz/list?cd=abc").build();
    }

    private DensukeScraper.ScheduleEntry entry(LocalDate date, int matchNumber) {
        DensukeScraper.ScheduleEntry e = new DensukeScraper.ScheduleEntry();
        e.setDate(date);
        e.setMatchNumber(matchNumber);
        return e;
    }

    @Test
    @DisplayName("伝助スクレイピング結果に無い試合番号を新規PENDING候補として検知し通知する")
    void detectsNewDeletionCandidate() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(Optional.of(url()));
        PracticeSession session = PracticeSession.builder().id(99L).organizationId(1L)
                .sessionDate(SESSION_DATE).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(List.of(session));
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.PENDING))
                .thenReturn(List.of());
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of());

        DensukeScraper.DensukeData scraped = new DensukeScraper.DensukeData();
        scraped.getEntries().add(entry(SESSION_DATE, 1));
        scraped.getEntries().add(entry(SESSION_DATE, 2));
        // matchNumber 3 は伝助側に存在しない → 削除候補

        service.detectDeletions(scraped, TARGET_MONTH, 1L);

        ArgumentCaptor<DensukeDeletionCandidate> captor = ArgumentCaptor.forClass(DensukeDeletionCandidate.class);
        verify(densukeDeletionCandidateRepository).save(captor.capture());
        DensukeDeletionCandidate saved = captor.getValue();
        assertThat(saved.getDensukeUrlId()).isEqualTo(10L);
        assertThat(saved.getOrganizationId()).isEqualTo(1L);
        assertThat(saved.getSessionDate()).isEqualTo(SESSION_DATE);
        assertThat(saved.getMatchNumber()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(DensukeDeletionCandidate.Status.PENDING);
        assertThat(saved.getNotifiedAt()).isNotNull();

        verify(lineNotificationService).sendDensukeDeletionCandidateDetectedNotification(eq(1L), anyList());
    }

    @Test
    @DisplayName("既にPENDINGの候補があれば重複登録・重複通知しない")
    void doesNotDuplicatePendingCandidate() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(Optional.of(url()));
        PracticeSession session = PracticeSession.builder().id(99L).organizationId(1L)
                .sessionDate(SESSION_DATE).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(List.of(session));

        DensukeDeletionCandidate existingPending = DensukeDeletionCandidate.builder()
                .id(1L).densukeUrlId(10L).sessionDate(SESSION_DATE).matchNumber(3)
                .status(DensukeDeletionCandidate.Status.PENDING).build();
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.PENDING))
                .thenReturn(List.of(existingPending));
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of());

        DensukeScraper.DensukeData scraped = new DensukeScraper.DensukeData();
        scraped.getEntries().add(entry(SESSION_DATE, 1));
        scraped.getEntries().add(entry(SESSION_DATE, 2));
        // matchNumber 3 は引き続き伝助側に存在しない（既存PENDINGのまま）

        service.detectDeletions(scraped, TARGET_MONTH, 1L);

        verify(densukeDeletionCandidateRepository, never()).save(any());
        verify(densukeDeletionCandidateRepository, never()).delete(any());
        verifyNoInteractions(lineNotificationService);
    }

    @Test
    @DisplayName("承認済み(APPROVED)の欠番は再検知しない")
    void skipsApprovedDeletions() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(Optional.of(url()));
        PracticeSession session = PracticeSession.builder().id(99L).organizationId(1L)
                .sessionDate(SESSION_DATE).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(List.of(session));
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.PENDING))
                .thenReturn(List.of());

        DensukeDeletionCandidate approved = DensukeDeletionCandidate.builder()
                .id(2L).densukeUrlId(10L).sessionDate(SESSION_DATE).matchNumber(3)
                .status(DensukeDeletionCandidate.Status.APPROVED).build();
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of(approved));

        DensukeScraper.DensukeData scraped = new DensukeScraper.DensukeData();
        scraped.getEntries().add(entry(SESSION_DATE, 1));
        scraped.getEntries().add(entry(SESSION_DATE, 2));

        service.detectDeletions(scraped, TARGET_MONTH, 1L);

        verify(densukeDeletionCandidateRepository, never()).save(any());
        verifyNoInteractions(lineNotificationService);
    }

    @Test
    @DisplayName("伝助側で行が復活したら未承認の削除候補を自動解消する")
    void autoResolvesWhenRowReappears() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(Optional.of(url()));
        PracticeSession session = PracticeSession.builder().id(99L).organizationId(1L)
                .sessionDate(SESSION_DATE).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(List.of(session));

        DensukeDeletionCandidate existingPending = DensukeDeletionCandidate.builder()
                .id(1L).densukeUrlId(10L).sessionDate(SESSION_DATE).matchNumber(3)
                .status(DensukeDeletionCandidate.Status.PENDING).build();
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.PENDING))
                .thenReturn(List.of(existingPending));
        when(densukeDeletionCandidateRepository.findByDensukeUrlIdAndStatus(10L, DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of());

        DensukeScraper.DensukeData scraped = new DensukeScraper.DensukeData();
        scraped.getEntries().add(entry(SESSION_DATE, 1));
        scraped.getEntries().add(entry(SESSION_DATE, 2));
        scraped.getEntries().add(entry(SESSION_DATE, 3)); // 行が復活

        service.detectDeletions(scraped, TARGET_MONTH, 1L);

        verify(densukeDeletionCandidateRepository).delete(existingPending);
        verify(densukeDeletionCandidateRepository, never()).save(any());
        verifyNoInteractions(lineNotificationService);
    }

    @Test
    @DisplayName("対象年月の伝助URLが未登録の場合は何もしない")
    void noOpWhenDensukeUrlNotFound() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 7, 1L))
                .thenReturn(Optional.empty());

        service.detectDeletions(new DensukeScraper.DensukeData(), TARGET_MONTH, 1L);

        verifyNoInteractions(practiceSessionRepository, densukeDeletionCandidateRepository, lineNotificationService);
    }
}
