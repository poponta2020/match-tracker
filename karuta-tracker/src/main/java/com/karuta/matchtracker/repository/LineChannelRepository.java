package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.ChannelType;
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

    /** 指定ステータス・用途のチャネル一覧を取得 */
    List<LineChannel> findByStatusAndChannelType(ChannelStatus status, ChannelType channelType);

    /** AVAILABLEなチャネルを用途別に1つ取得（割り当て用） */
    Optional<LineChannel> findFirstByStatusAndChannelTypeOrderByIdAsc(ChannelStatus status, ChannelType channelType);

    /** 用途別のチャネル一覧を取得 */
    List<LineChannel> findAllByChannelType(ChannelType channelType);

    /** 配信グループに割り当てられたチャネル（bot）一覧を取得 */
    List<LineChannel> findByBroadcastGroupId(Long broadcastGroupId);

    /** 配信グループに割り当てられた指定用途のチャネル一覧を取得 */
    List<LineChannel> findByBroadcastGroupIdAndChannelType(Long broadcastGroupId, ChannelType channelType);

    /**
     * 当月送信数を原子的に加算する（全体配信の消費即時反映・AC-5）。
     * 毎時同期 {@code LineMessageCountSyncScheduler} が後で実測値で上書き補正する。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE LineChannel c SET c.monthlyMessageCount = c.monthlyMessageCount + :delta WHERE c.id = :id")
    int incrementMonthlyMessageCount(@Param("id") Long id, @Param("delta") int delta);

}
