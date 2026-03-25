package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
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

    /** LINE発行のチャネルIDで検索 */
    Optional<LineChannel> findByLineChannelId(String lineChannelId);

    /** 指定ステータスのチャネル一覧を取得 */
    List<LineChannel> findByStatus(ChannelStatus status);

    /** AVAILABLEなチャネルを1つ取得（割り当て用） */
    Optional<LineChannel> findFirstByStatusOrderByIdAsc(ChannelStatus status);

    /** 全チャネルの月間送信数をリセット */
    @Modifying
    @Query("UPDATE LineChannel c SET c.monthlyMessageCount = 0, c.messageCountResetAt = CURRENT_TIMESTAMP")
    void resetAllMonthlyMessageCounts();
}
