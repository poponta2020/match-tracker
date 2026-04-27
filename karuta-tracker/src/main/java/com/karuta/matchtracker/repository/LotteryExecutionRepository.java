package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 抽選実行履歴リポジトリ
 */
@Repository
public interface LotteryExecutionRepository extends JpaRepository<LotteryExecution, Long> {

    /**
     * 指定年月の抽選実行履歴を取得
     */
    List<LotteryExecution> findByTargetYearAndTargetMonth(int targetYear, int targetMonth);

    /**
     * 指定年月・指定団体の抽選実行履歴を取得
     */
    List<LotteryExecution> findByTargetYearAndTargetMonthAndOrganizationId(
            int targetYear, int targetMonth, Long organizationId);

    /**
     * 指定年月かつ複数団体の抽選実行履歴を取得
     */
    List<LotteryExecution> findByTargetYearAndTargetMonthAndOrganizationIdIn(
            int targetYear, int targetMonth, List<Long> organizationIds);

    /**
     * 指定年月の成功した抽選が存在するか確認（団体指定なし）
     */
    boolean existsByTargetYearAndTargetMonthAndStatus(int targetYear, int targetMonth, ExecutionStatus status);

    /**
     * 指定年月・団体の成功した抽選が存在するか確認
     */
    boolean existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
            int targetYear, int targetMonth, Long organizationId, ExecutionStatus status);

    /**
     * 指定セッションの最新抽選実行を取得
     */
    Optional<LotteryExecution> findTopBySessionIdOrderByExecutedAtDesc(Long sessionId);

    /**
     * 指定セッションIDリストの中で抽選が実行されたセッションIDリストを取得
     */
    List<LotteryExecution> findBySessionIdIn(List<Long> sessionIds);

    /**
     * 指定年月の最新の成功した抽選実行を取得（団体指定なし）
     */
    Optional<LotteryExecution> findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
            int targetYear, int targetMonth, ExecutionStatus status);

    /**
     * 指定年月・団体の最新の成功した抽選実行を取得
     */
    Optional<LotteryExecution> findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
            int targetYear, int targetMonth, Long organizationId, ExecutionStatus status);

    /**
     * 指定年月の全団体対象（organization_id IS NULL）の最新の成功した抽選実行を取得
     */
    Optional<LotteryExecution> findTopByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatusOrderByExecutedAtDesc(
            int targetYear, int targetMonth, ExecutionStatus status);
}
