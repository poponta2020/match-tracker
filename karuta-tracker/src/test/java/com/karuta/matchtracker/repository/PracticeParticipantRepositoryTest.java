package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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

    private PracticeParticipant participant(Long sessionId, Long playerId, Integer matchNumber,
                                            ParticipantStatus status) {
        return practiceParticipantRepository.save(PracticeParticipant.builder()
                .sessionId(sessionId).playerId(playerId).matchNumber(matchNumber).status(status).build());
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
}
