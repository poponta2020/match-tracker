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
     * 指定年月の成功した自動抽選が存在するか確認
     */
    boolean existsByTargetYearAndTargetMonthAndStatus(int targetYear, int targetMonth, ExecutionStatus status);

    /**
     * 指定セッションの最新抽選実行を取得
     */
    Optional<LotteryExecution> findTopBySessionIdOrderByExecutedAtDesc(Long sessionId);

    /**
     * 指定セッションIDリストの中で抽選が実行されたセッションIDリストを取得
     */
    List<LotteryExecution> findBySessionIdIn(List<Long> sessionIds);

    /**
     * 指定年月の最新の成功した抽選実行を取得
     */
    Optional<LotteryExecution> findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
            int targetYear, int targetMonth, ExecutionStatus status);
}
