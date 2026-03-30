package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 練習日情報のRepositoryインターフェース
 *
 * 練習日の管理、検索クエリを提供します。
 */
@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    /**
     * 日付で練習日を検索
     *
     * @param sessionDate 練習日
     * @return 練習日情報（任意）
     */
    Optional<PracticeSession> findBySessionDate(LocalDate sessionDate);

    /**
     * 期間内の練習日を取得（日付の昇順）
     */
    @Query("SELECT ps FROM PracticeSession ps WHERE ps.sessionDate BETWEEN :startDate AND :endDate ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findByDateRange(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 特定の年月の練習日を取得
     *
     * @param year 年
     * @param month 月
     * @return 練習日のリスト
     */
    @Query("SELECT ps FROM PracticeSession ps WHERE YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * 日付が練習日として登録されているか確認
     *
     * @param sessionDate 日付
     * @return 登録されている場合true
     */
    boolean existsBySessionDate(LocalDate sessionDate);

    boolean existsBySessionDateAndOrganizationId(LocalDate sessionDate, Long organizationId);

    Optional<PracticeSession> findBySessionDateAndOrganizationId(LocalDate sessionDate, Long organizationId);

    @Query("SELECT ps FROM PracticeSession ps WHERE YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month AND ps.organizationId = :organizationId ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findByYearAndMonthAndOrganizationId(@Param("year") int year, @Param("month") int month, @Param("organizationId") Long organizationId);

    // === 団体フィルタ付きクエリ ===

    @Query("SELECT ps FROM PracticeSession ps WHERE ps.organizationId IN :orgIds AND YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findByOrganizationIdInAndYearAndMonth(@Param("orgIds") List<Long> orgIds, @Param("year") int year, @Param("month") int month);

    @Query("SELECT ps FROM PracticeSession ps WHERE ps.organizationId IN :orgIds AND ps.sessionDate >= :date ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findUpcomingSessionsByOrganizationIdIn(@Param("orgIds") List<Long> orgIds, @Param("date") LocalDate date);

    @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.organizationId IN :orgIds AND ps.sessionDate >= :date ORDER BY ps.sessionDate DESC")
    List<LocalDate> findSessionDatesByOrganizationIdIn(@Param("orgIds") List<Long> orgIds, @Param("date") LocalDate date);

    /**
     * 指定日以降の練習日の日付リストのみ取得（降順）
     *
     * @param date 基準日
     * @return 日付リスト
     */
    @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.sessionDate >= :date ORDER BY ps.sessionDate DESC")
    List<LocalDate> findSessionDates(@Param("date") LocalDate date);
}
