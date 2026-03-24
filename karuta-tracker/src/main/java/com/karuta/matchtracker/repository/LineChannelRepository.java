package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LINEチャネルリポジトリ
 */
@Repository
public interface LineChannelRepository extends JpaRepository<LineChannel, Long> {

    /**
     * ステータスで検索
     */
    List<LineChannel> findByStatus(LineChannelStatus status);

    /**
     * 利用可能な最初のチャネルを取得
     */
    Optional<LineChannel> findFirstByStatusOrderByIdAsc(LineChannelStatus status);

    /**
     * ステータスごとのチャネル数を取得
     */
    long countByStatus(LineChannelStatus status);

    /**
     * 全チャネルの月間送信数をリセット
     */
    @Modifying
    @Query("UPDATE LineChannel c SET c.monthlyMessageCount = 0, c.messageCountResetAt = CURRENT_TIMESTAMP")
    void resetAllMonthlyMessageCounts();
}
