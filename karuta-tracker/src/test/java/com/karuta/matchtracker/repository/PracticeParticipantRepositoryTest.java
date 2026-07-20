package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * PracticeParticipantRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("PracticeParticipantRepository 結合テスト")
class PracticeParticipantRepositoryTest {

    @Autowired
    private PracticeParticipantRepository practiceParticipantRepository;

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    private static final Long ORG_1 = 1L;
    private static final Long ORG_2 = 2L;

    private PracticeParticipant participant(Long sessionId, Long playerId, Integer matchNumber,
                                            ParticipantStatus status) {
        return practiceParticipantRepository.save(PracticeParticipant.builder()
                .sessionId(sessionId).playerId(playerId).matchNumber(matchNumber).status(status).build());
    }

    private PracticeSession createSession(LocalDate date, Long organizationId) {
        return practiceSessionRepository.save(PracticeSession.builder()
                .sessionDate(date)
                .totalMatches(5)
                .organizationId(organizationId)
                .createdBy(1L)
                .updatedBy(1L)
                .build());
    }

    @Test
    @DisplayName("existsActiveBySessionIdAndPlayerId は WON/PENDING のみ true、WAITLISTED/OFFERED/CANCELLED は false")
    void existsActiveBySessionIdAndPlayerId_onlyWonPending() {
        participant(1L, 10L, 1, ParticipantStatus.WON);
        participant(2L, 20L, 1, ParticipantStatus.PENDING);
        participant(3L, 30L, 1, ParticipantStatus.WAITLISTED);
        participant(4L, 40L, 1, ParticipantStatus.OFFERED);
        participant(5L, 50L, 1, ParticipantStatus.CANCELLED);

        // WON / PENDING は参加確定 → true
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(1L, 10L)).isTrue();
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(2L, 20L)).isTrue();
        // WAITLISTED / OFFERED（未確定）・CANCELLED は false（セッション特定に使わない）
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(3L, 30L)).isFalse();
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(4L, 40L)).isFalse();
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(5L, 50L)).isFalse();
        // 参加記録のない選手は false
        assertThat(practiceParticipantRepository.existsActiveBySessionIdAndPlayerId(1L, 99L)).isFalse();
    }

    // ===== findWonPlayerDates テスト =====

    @Test
    @DisplayName("範囲内のWON参加行が (playerId, sessionDate) で返る")
    void testFindWonPlayerDates_ReturnsWonRowsInRange() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession session = createSession(LocalDate.of(2026, 7, 10), ORG_1);
        participant(session.getId(), 100L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerId()).isEqualTo(100L);
        assertThat(result.get(0).getSessionDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    @DisplayName("同一選手が同一日に複数WONなら複数行返る（DISTINCTしない）")
    void testFindWonPlayerDates_SamePlayerSameDay_ReturnsMultipleRows() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession session = createSession(LocalDate.of(2026, 7, 10), ORG_1);
        participant(session.getId(), 100L, 1, ParticipantStatus.WON);
        participant(session.getId(), 100L, 2, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getPlayerId().equals(100L)
                && r.getSessionDate().equals(LocalDate.of(2026, 7, 10)));
    }

    @Test
    @DisplayName("同一選手が範囲内の異なる2日にWONなら2行返る（潰さない）")
    void testFindWonPlayerDates_SamePlayerDifferentDays_ReturnsMultipleRows() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession session1 = createSession(LocalDate.of(2026, 7, 5), ORG_1);
        PracticeSession session2 = createSession(LocalDate.of(2026, 7, 15), ORG_1);
        participant(session1.getId(), 100L, 1, ParticipantStatus.WON);
        participant(session2.getId(), 100L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(
                        PracticeParticipantRepository.PlayerWonDate::getPlayerId,
                        PracticeParticipantRepository.PlayerWonDate::getSessionDate)
                .containsExactlyInAnyOrder(
                        tuple(100L, LocalDate.of(2026, 7, 5)),
                        tuple(100L, LocalDate.of(2026, 7, 15)));
    }

    @Test
    @DisplayName("非WON（PENDING/WAITLISTED/OFFERED/CANCELLED）は除外される")
    void testFindWonPlayerDates_ExcludesNonWonStatuses() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession session = createSession(LocalDate.of(2026, 7, 10), ORG_1);
        participant(session.getId(), 101L, 1, ParticipantStatus.PENDING);
        participant(session.getId(), 102L, 2, ParticipantStatus.WAITLISTED);
        participant(session.getId(), 103L, 3, ParticipantStatus.OFFERED);
        participant(session.getId(), 104L, 4, ParticipantStatus.CANCELLED);
        participant(session.getId(), 105L, 5, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerId()).isEqualTo(105L);
    }

    @Test
    @DisplayName("範囲外（fromより前・to以上）のWONは除外される")
    void testFindWonPlayerDates_ExcludesOutOfRangeDates() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 10);
        LocalDate to = LocalDate.of(2026, 7, 20);
        PracticeSession beforeSession = createSession(LocalDate.of(2026, 7, 9), ORG_1);
        PracticeSession afterSession = createSession(LocalDate.of(2026, 7, 21), ORG_1);
        PracticeSession inRangeSession = createSession(LocalDate.of(2026, 7, 15), ORG_1);
        participant(beforeSession.getId(), 100L, 1, ParticipantStatus.WON);
        participant(afterSession.getId(), 101L, 1, ParticipantStatus.WON);
        participant(inRangeSession.getId(), 102L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("境界: sessionDate==from は含む・sessionDate==to は含まない")
    void testFindWonPlayerDates_BoundaryInclusiveFromExclusiveTo() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 10);
        LocalDate to = LocalDate.of(2026, 7, 20);
        PracticeSession fromSession = createSession(from, ORG_1);
        PracticeSession toSession = createSession(to, ORG_1);
        participant(fromSession.getId(), 100L, 1, ParticipantStatus.WON);
        participant(toSession.getId(), 101L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerId()).isEqualTo(100L);
        assertThat(result.get(0).getSessionDate()).isEqualTo(from);
    }

    @Test
    @DisplayName("団体フィルタ: organizationId指定時は別団体のWONを含まない")
    void testFindWonPlayerDates_FiltersByOrganization() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession org1Session = createSession(LocalDate.of(2026, 7, 10), ORG_1);
        PracticeSession org2Session = createSession(LocalDate.of(2026, 7, 10), ORG_2);
        participant(org1Session.getId(), 100L, 1, ParticipantStatus.WON);
        participant(org2Session.getId(), 200L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(ORG_1, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("団体フィルタ: organizationId=null なら全団体のWONが返る")
    void testFindWonPlayerDates_NullOrganizationId_ReturnsAllOrganizations() {
        // Given
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PracticeSession org1Session = createSession(LocalDate.of(2026, 7, 10), ORG_1);
        PracticeSession org2Session = createSession(LocalDate.of(2026, 7, 11), ORG_2);
        participant(org1Session.getId(), 100L, 1, ParticipantStatus.WON);
        participant(org2Session.getId(), 200L, 1, ParticipantStatus.WON);

        // When
        List<PracticeParticipantRepository.PlayerWonDate> result =
                practiceParticipantRepository.findWonPlayerDates(null, from, to);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PracticeParticipantRepository.PlayerWonDate::getPlayerId)
                .containsExactlyInAnyOrder(100L, 200L);
    }
}
