package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
