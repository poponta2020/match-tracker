package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.DensukeMemberMapping;
import com.karuta.matchtracker.repository.DensukeMemberMappingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * saveMemberMapping の実DBトランザクション回帰テスト（Issue #1036）
 *
 * DensukeMemberMapping は IDENTITY 採番のため save() 時点で即 INSERT が発行され、
 * 一意制約違反が起きると PostgreSQL は現トランザクション全体を abort 状態（25P02）にする。
 * INSERT を呼び出し元と同一トランザクションで行うと、TOCTOU 競合 1 件で catch 後の
 * 救済クエリもバッチ内の後続 DB 操作もすべて失敗し、コミットが
 * UnexpectedRollbackException になってバッチ全体が破棄される。
 * モックでは PostgreSQL のトランザクション abort を再現できないため、
 * TestContainers の実 PostgreSQL + 実トランザクションで検証する（PR #1035 と同パターン）。
 */
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("saveMemberMapping 統合回帰テスト（トランザクション abort）")
class DensukeMemberMappingTxIntegrationTest {

    @Autowired
    private DensukeWriteService densukeWriteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** TOCTOU競合テストで事前チェックのすり抜けを再現するための spy（他のテストでは素通し） */
    @MockitoSpyBean
    private DensukeMemberMappingRepository densukeMemberMappingRepository;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        resetTable();
        txTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        resetTable();
    }

    private void resetTable() {
        jdbcTemplate.execute("TRUNCATE TABLE densuke_member_mappings RESTART IDENTITY CASCADE");
    }

    /** 「別トランザクションで先に登録された」状態を auto-commit で作る */
    private void insertCommittedMapping(long urlId, long playerId, String mi) {
        jdbcTemplate.update(
                "INSERT INTO densuke_member_mappings (densuke_url_id, player_id, densuke_member_id, created_at) " +
                "VALUES (?, ?, ?, NOW())", urlId, playerId, mi);
    }

    /**
     * 事前チェック（1回目の findByDensukeUrlIdAndDensukeMemberId）だけ空振りさせ、
     * 「チェック後・INSERT 前に並行トランザクションが同じマッピングを登録した」
     * TOCTOU 競合を単一スレッドで再現する。
     *
     * <p>2回目以降（catch 内の救済クエリ）は insertCommittedMapping で入れたコミット済み行と
     * 同内容を返す。spy の実体が JDK プロキシのため doCallRealMethod は使えない。
     * 外側トランザクションが abort していないこと自体は、テスト本体の後続 save（素通し・実DB）と
     * コミット成功・コミット後のカウントで検証する。
     */
    private void simulateToctouWindow(long urlId, String mi, long committedPlayerId) {
        DensukeMemberMapping committed = DensukeMemberMapping.builder()
                .densukeUrlId(urlId).playerId(committedPlayerId).densukeMemberId(mi).build();
        doReturn(Optional.empty(), Optional.of(committed))
                .when(densukeMemberMappingRepository).findByDensukeUrlIdAndDensukeMemberId(urlId, mi);
    }

    private int countByMi(String mi) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM densuke_member_mappings WHERE densuke_member_id = ?", Integer.class, mi);
        return count != null ? count : -1;
    }

    @Test
    @DisplayName("回帰: TOCTOU競合（同一プレイヤー）でも外側トランザクションが abort せずバッチが継続・コミットできる（Issue #1036）")
    void toctouSamePlayer_outerTransactionSurvivesAndCommits() {
        insertCommittedMapping(1L, 100L, "mi1");
        simulateToctouWindow(1L, "mi1", 100L);

        Boolean result = txTemplate.execute(status -> {
            boolean r = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "選手A");

            // 回帰の核心1: 同一トランザクションの後続 DB 操作が 25P02 で失敗しない。
            // 修正前は saveMemberMapping の catch 内救済クエリが abort 済みトランザクション上で
            // 例外になり、そもそもここへ到達しない。
            densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                    .densukeUrlId(1L).playerId(300L).densukeMemberId("mi9").build());
            return r;
        });

        // 同一プレイヤーの TOCTOU 競合は成功扱い（既存契約）
        assertThat(result).isTrue();
        // 回帰の核心2: 外側コミットが成功し、衝突以外のバッチ内書き込みが永続化される
        assertThat(countByMi("mi9")).isEqualTo(1);
        // 衝突した mi1 は重複せず 1 件のまま
        assertThat(countByMi("mi1")).isEqualTo(1);
    }

    @Test
    @DisplayName("回帰: TOCTOU競合（別プレイヤー）は false を返しつつ外側トランザクションは健全なまま")
    void toctouDifferentPlayer_returnsFalse_outerTransactionSurvives() {
        insertCommittedMapping(1L, 200L, "mi1");
        simulateToctouWindow(1L, "mi1", 200L);

        Boolean result = txTemplate.execute(status -> {
            boolean r = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "選手A");
            densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                    .densukeUrlId(1L).playerId(300L).densukeMemberId("mi9").build());
            return r;
        });

        // 別プレイヤーに取られていた場合は保存失敗の契約（呼び出し元がエラー記録してスキップ）
        assertThat(result).isFalse();
        // バッチの後続書き込みは破棄されない
        assertThat(countByMi("mi9")).isEqualTo(1);
        // 既存マッピングは上書きされない
        Long mappedPlayer = jdbcTemplate.queryForObject(
                "SELECT player_id FROM densuke_member_mappings WHERE densuke_member_id = 'mi1'", Long.class);
        assertThat(mappedPlayer).isEqualTo(200L);
    }

    @Test
    @DisplayName("競合なしの通常パス: 外側トランザクション内から保存でき、コミット後も残る")
    void noConflict_insertSucceedsInsideOuterTransaction() {
        Boolean result = txTemplate.execute(status ->
                densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "選手A"));

        assertThat(result).isTrue();
        assertThat(countByMi("mi1")).isEqualTo(1);
        Long mappedPlayer = jdbcTemplate.queryForObject(
                "SELECT player_id FROM densuke_member_mappings WHERE densuke_member_id = 'mi1'", Long.class);
        assertThat(mappedPlayer).isEqualTo(100L);
    }
}
