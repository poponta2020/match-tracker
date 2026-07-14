package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PracticeSession;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT ps FROM PracticeSession ps WHERE ps.organizationId IN :orgIds AND ps.sessionDate >= :date ORDER BY ps.sessionDate ASC, ps.startTime ASC NULLS FIRST, ps.id ASC")
    List<PracticeSession> findUpcomingSessionsByOrganizationIdIn(@Param("orgIds") List<Long> orgIds, @Param("date") LocalDate date);

    @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.organizationId IN :orgIds AND ps.sessionDate >= :date ORDER BY ps.sessionDate DESC")
    List<LocalDate> findSessionDatesByOrganizationIdIn(@Param("orgIds") List<Long> orgIds, @Param("date") LocalDate date);

    /**
     * 指定団体・指定日より前の練習日を降順で取得（自動マッチングの「前回練習日」特定用）。
     *
     * 過去30日窓に依存せず、団体の過去セッションを遡って直近の対戦がある練習日を
     * 特定するために使う。呼び出し側で {@link Pageable} により取得件数を上限付けする。
     *
     * @param orgId 団体ID
     * @param date 基準日（この日は含まない）
     * @param pageable 取得件数の上限
     * @return 練習日の日付リスト（新しい順）
     */
    @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.organizationId = :orgId AND ps.sessionDate < :date ORDER BY ps.sessionDate DESC")
    List<LocalDate> findPastSessionDatesByOrganizationId(@Param("orgId") Long orgId, @Param("date") LocalDate date, Pageable pageable);

    /**
     * 指定日以降の直近の練習セッションを1件取得
     */
    Optional<PracticeSession> findFirstBySessionDateGreaterThanEqualOrderBySessionDateAsc(LocalDate date);

    /**
     * 指定日以降の練習日の日付リストのみ取得（降順）
     *
     * @param date 基準日
     * @return 日付リスト
     */
    @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.sessionDate >= :date ORDER BY ps.sessionDate DESC")
    List<LocalDate> findSessionDates(@Param("date") LocalDate date);

    /**
     * 指定日の practice_sessions の venue_id を重複排除して取得（venue_id IS NOT NULL のみ）
     * Match の venue_id 決定で「同日一意なら採用」のチェックに使用する。
     */
    @Query("SELECT DISTINCT ps.venueId FROM PracticeSession ps " +
           "WHERE ps.sessionDate = :sessionDate AND ps.venueId IS NOT NULL")
    List<Long> findDistinctVenueIdsBySessionDate(@Param("sessionDate") LocalDate sessionDate);
}
