package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 練習日管理サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PracticeSessionService {

    private final PracticeSessionRepository practiceSessionRepository;

    /**
     * 全ての練習日を取得（降順）
     */
    public List<PracticeSessionDto> findAllSessions() {
        log.debug("Finding all practice sessions");
        return practiceSessionRepository.findAllOrderBySessionDateDesc()
                .stream()
                .map(PracticeSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * IDで練習日を取得
     */
    public PracticeSessionDto findById(Long id) {
        log.debug("Finding practice session by id: {}", id);
        PracticeSession session = practiceSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", id));
        return PracticeSessionDto.fromEntity(session);
    }

    /**
     * 日付で練習日を取得
     */
    public PracticeSessionDto findByDate(LocalDate date) {
        log.debug("Finding practice session by date: {}", date);
        PracticeSession session = practiceSessionRepository.findBySessionDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", date));
        return PracticeSessionDto.fromEntity(session);
    }

    /**
     * 期間内の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsInRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Finding practice sessions between {} and {}", startDate, endDate);
        return practiceSessionRepository.findByDateRange(startDate, endDate)
                .stream()
                .map(PracticeSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 特定の年月の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsByYearMonth(int year, int month) {
        log.debug("Finding practice sessions for {}-{}", year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        return practiceSessionRepository.findByYearAndMonth(yearMonth.getYear(), yearMonth.getMonthValue())
                .stream()
                .map(PracticeSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 指定日以降の練習日を取得
     */
    public List<PracticeSessionDto> findUpcomingSessions(LocalDate fromDate) {
        log.debug("Finding upcoming practice sessions from {}", fromDate);
        return practiceSessionRepository.findUpcomingSessions(fromDate)
                .stream()
                .map(PracticeSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 日付が練習日として登録されているか確認
     */
    public boolean existsSessionOnDate(LocalDate date) {
        log.debug("Checking if practice session exists on {}", date);
        return practiceSessionRepository.existsBySessionDate(date);
    }

    /**
     * 練習日を新規登録
     */
    @Transactional
    public PracticeSessionDto createSession(PracticeSessionCreateRequest request) {
        log.info("Creating new practice session on {}", request.getSessionDate());

        // 日付の重複チェック
        if (practiceSessionRepository.existsBySessionDate(request.getSessionDate())) {
            throw new DuplicateResourceException("PracticeSession", "sessionDate", request.getSessionDate());
        }

        PracticeSession session = request.toEntity();
        PracticeSession saved = practiceSessionRepository.save(session);

        log.info("Successfully created practice session with id: {}", saved.getId());
        return PracticeSessionDto.fromEntity(saved);
    }

    /**
     * 総試合数を更新
     */
    @Transactional
    public PracticeSessionDto updateTotalMatches(Long id, Integer totalMatches) {
        log.info("Updating total matches for practice session id: {}", id);

        PracticeSession session = practiceSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", id));

        if (totalMatches < 0) {
            throw new IllegalArgumentException("Total matches cannot be negative");
        }

        session.setTotalMatches(totalMatches);
        PracticeSession updated = practiceSessionRepository.save(session);

        log.info("Successfully updated total matches for practice session id: {}", id);
        return PracticeSessionDto.fromEntity(updated);
    }

    /**
     * 練習日を削除
     */
    @Transactional
    public void deleteSession(Long id) {
        log.info("Deleting practice session with id: {}", id);

        if (!practiceSessionRepository.existsById(id)) {
            throw new ResourceNotFoundException("PracticeSession", id);
        }

        practiceSessionRepository.deleteById(id);
        log.info("Successfully deleted practice session with id: {}", id);
    }
}
