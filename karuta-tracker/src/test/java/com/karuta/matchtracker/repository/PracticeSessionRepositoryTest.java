package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.PracticeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PracticeSessionRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("PracticeSessionRepository 結合テスト")
class PracticeSessionRepositoryTest {

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    private LocalDate today;
    private LocalDate tomorrow;
    private LocalDate nextWeek;
    private LocalDate lastWeek;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        tomorrow = today.plusDays(1);
        nextWeek = today.plusWeeks(1);
        lastWeek = today.minusWeeks(1);

        // テストデータの準備
        PracticeSession session1 = PracticeSession.builder()
                .sessionDate(lastWeek)
                .totalMatches(4)
                .build();

        PracticeSession session2 = PracticeSession.builder()
                .sessionDate(today)
                .totalMatches(5)
                .build();

        PracticeSession session3 = PracticeSession.builder()
                .sessionDate(tomorrow)
                .totalMatches(3)
                .build();

        PracticeSession session4 = PracticeSession.builder()
                .sessionDate(nextWeek)
                .totalMatches(4)
                .build();

        practiceSessionRepository.saveAll(List.of(session1, session2, session3, session4));
    }

    @Test
    @DisplayName("日付で練習日を検索できる")
    void testFindBySessionDate() {
        // When
        Optional<PracticeSession> found = practiceSessionRepository.findBySessionDate(today);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTotalMatches()).isEqualTo(5);
    }

    @Test
    @DisplayName("存在しない日付で検索すると空のOptionalが返る")
    void testFindBySessionDateNotFound() {
        // When
        Optional<PracticeSession> found = practiceSessionRepository
                .findBySessionDate(today.plusDays(10));

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("期間内の練習日を取得できる（昇順）")
    void testFindByDateRange() {
        // When
        List<PracticeSession> sessions = practiceSessionRepository
                .findByDateRange(today, nextWeek);

        // Then
        assertThat(sessions).hasSize(3);
        assertThat(sessions.get(0).getSessionDate()).isEqualTo(today);
        assertThat(sessions.get(1).getSessionDate()).isEqualTo(tomorrow);
        assertThat(sessions.get(2).getSessionDate()).isEqualTo(nextWeek);
    }

    @Test
    @DisplayName("指定日以降の練習日を取得できる")
    void testFindUpcomingSessions() {
        // When
        List<PracticeSession> upcomingSessions = practiceSessionRepository
                .findUpcomingSessions(today);

        // Then
        assertThat(upcomingSessions).hasSize(3);
        assertThat(upcomingSessions)
                .extracting(PracticeSession::getSessionDate)
                .containsExactly(today, tomorrow, nextWeek);
    }

    @Test
    @DisplayName("全ての練習日を取得できる（降順）")
    void testFindAllOrderBySessionDateDesc() {
        // When
        List<PracticeSession> allSessions = practiceSessionRepository
                .findAllOrderBySessionDateDesc();

        // Then
        assertThat(allSessions).hasSize(4);
        assertThat(allSessions.get(0).getSessionDate()).isEqualTo(nextWeek);
        assertThat(allSessions.get(1).getSessionDate()).isEqualTo(tomorrow);
        assertThat(allSessions.get(2).getSessionDate()).isEqualTo(today);
        assertThat(allSessions.get(3).getSessionDate()).isEqualTo(lastWeek);
    }

    @Test
    @DisplayName("特定の年月の練習日を取得できる")
    void testFindByYearAndMonth() {
        // When
        int year = today.getYear();
        int month = today.getMonthValue();
        List<PracticeSession> sessions = practiceSessionRepository
                .findByYearAndMonth(year, month);

        // Then - 今月の練習日のみ取得できる
        assertThat(sessions).isNotEmpty();
        assertThat(sessions)
                .allMatch(s -> s.getSessionDate().getYear() == year &&
                              s.getSessionDate().getMonthValue() == month);
    }

    @Test
    @DisplayName("日付が練習日として登録されているか確認できる")
    void testExistsBySessionDate() {
        // When & Then
        assertThat(practiceSessionRepository.existsBySessionDate(today)).isTrue();
        assertThat(practiceSessionRepository.existsBySessionDate(today.plusDays(5))).isFalse();
    }

    @Test
    @DisplayName("練習日を保存するとタイムスタンプが自動設定される")
    void testTimestampAutoSet() {
        // Given
        PracticeSession session = PracticeSession.builder()
                .sessionDate(today.plusDays(20))
                .totalMatches(6)
                .build();

        // When
        PracticeSession saved = practiceSessionRepository.save(session);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("同じ日付の練習日は登録できない（UNIQUE制約）")
    void testUniqueSessionDate() {
        // Given
        PracticeSession duplicate = PracticeSession.builder()
                .sessionDate(today)  // 既に存在する日付
                .totalMatches(10)
                .build();

        // When & Then
        try {
            practiceSessionRepository.saveAndFlush(duplicate);
            assertThat(false).as("UNIQUE制約違反の例外が発生すべき").isTrue();
        } catch (Exception e) {
            // UNIQUE制約違反で例外が発生することを確認
            assertThat(e).isNotNull();
        }
    }
}
