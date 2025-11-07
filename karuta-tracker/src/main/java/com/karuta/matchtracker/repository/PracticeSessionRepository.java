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
     *
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 練習日のリスト
     */
    @Query("SELECT ps FROM PracticeSession ps WHERE ps.sessionDate BETWEEN :startDate AND :endDate ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findByDateRange(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 指定日以降の練習日を取得（日付の昇順）
     *
     * @param date 基準日
     * @return 練習日のリスト
     */
    @Query("SELECT ps FROM PracticeSession ps WHERE ps.sessionDate >= :date ORDER BY ps.sessionDate ASC")
    List<PracticeSession> findUpcomingSessions(@Param("date") LocalDate date);

    /**
     * 全ての練習日を取得（日付の降順）
     *
     * @return 練習日のリスト
     */
    @Query("SELECT ps FROM PracticeSession ps ORDER BY ps.sessionDate DESC")
    List<PracticeSession> findAllOrderBySessionDateDesc();

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
}
