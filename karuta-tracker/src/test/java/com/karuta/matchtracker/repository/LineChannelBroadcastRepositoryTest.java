package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-2: GROUP 種別チャネルが個人割当（PLAYER）プールにカニバリしないことを検証する結合テスト。
 * 個人割当 {@code assignChannel} が使う type-aware 検索が GROUP を絶対に掴まないことを担保する。
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("LineChannel GROUP種別分離 結合テスト")
class LineChannelBroadcastRepositoryTest {

    @Autowired
    private LineChannelRepository repository;

    private LineChannel newChannel(String lineChannelId, ChannelType type, ChannelStatus status) {
        return LineChannel.builder()
                .lineChannelId(lineChannelId)
                .channelSecret("secret-" + lineChannelId)
                .channelAccessToken("token-" + lineChannelId)
                .channelType(type)
                .status(status)
                .build();
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("AVAILABLE・PLAYER 検索は GROUP 種別チャネルを絶対に返さない")
    void playerFinderNeverPicksGroup() {
        // GROUP/AVAILABLE のみ在庫（PLAYER 在庫なし）
        repository.save(newChannel("group-1", ChannelType.GROUP, ChannelStatus.AVAILABLE));
        repository.save(newChannel("group-2", ChannelType.GROUP, ChannelStatus.AVAILABLE));

        Optional<LineChannel> playerPick =
                repository.findFirstByStatusAndChannelTypeOrderByIdAsc(ChannelStatus.AVAILABLE, ChannelType.PLAYER);
        assertThat(playerPick).isEmpty();

        // GROUP 検索なら掴める
        Optional<LineChannel> groupPick =
                repository.findFirstByStatusAndChannelTypeOrderByIdAsc(ChannelStatus.AVAILABLE, ChannelType.GROUP);
        assertThat(groupPick).isPresent();
    }

    @Test
    @DisplayName("PLAYER と GROUP が混在しても PLAYER 検索は PLAYER のみを返す")
    void playerFinderReturnsOnlyPlayerWhenMixed() {
        LineChannel player = repository.save(newChannel("player-1", ChannelType.PLAYER, ChannelStatus.AVAILABLE));
        repository.save(newChannel("group-1", ChannelType.GROUP, ChannelStatus.AVAILABLE));

        Optional<LineChannel> pick =
                repository.findFirstByStatusAndChannelTypeOrderByIdAsc(ChannelStatus.AVAILABLE, ChannelType.PLAYER);
        assertThat(pick).isPresent();
        assertThat(pick.get().getId()).isEqualTo(player.getId());
        assertThat(pick.get().getChannelType()).isEqualTo(ChannelType.PLAYER);
    }

    @Test
    @DisplayName("findByBroadcastGroupId は割り当てられた bot のみを返す")
    void findByBroadcastGroupId() {
        LineChannel bot1 = newChannel("bot-1", ChannelType.GROUP, ChannelStatus.AVAILABLE);
        bot1.setBroadcastGroupId(5L);
        LineChannel bot2 = newChannel("bot-2", ChannelType.GROUP, ChannelStatus.AVAILABLE);
        bot2.setBroadcastGroupId(5L);
        LineChannel unassigned = newChannel("bot-3", ChannelType.GROUP, ChannelStatus.AVAILABLE);
        repository.saveAll(List.of(bot1, bot2, unassigned));

        List<LineChannel> assigned = repository.findByBroadcastGroupId(5L);
        assertThat(assigned).hasSize(2);
        assertThat(assigned).extracting(LineChannel::getLineChannelId)
                .containsExactlyInAnyOrder("bot-1", "bot-2");
    }
}
