package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;

/**
 * {@link OrganizationService#ensurePlayerBelongsToOrganization} の実DB（Testcontainers/PostgreSQL）統合テスト。
 *
 * <p>Issue #1037 の回帰テスト: 事前 exists チェック通過後に別リクエストが同一
 * (player_id, organization_id) を先に登録した TOCTOU 競合で、一意制約違反が
 * 呼び出し元トランザクションを rollback-only にマークし、コミット時に
 * {@link org.springframework.transaction.UnexpectedRollbackException} で 500 になる
 * バグを検証する。トランザクション境界の挙動が本質のため、テストメソッドの
 * 自動トランザクションは無効化（{@code NOT_SUPPORTED}）し、呼び出し元の業務
 * トランザクションを {@link TransactionTemplate} で明示的に模す。</p>
 */
@DataJpaTest
@Import({TestContainersConfig.class, OrganizationService.class})
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("OrganizationService 自動所属 結合テスト（並列競合の回帰）")
class OrganizationServiceEnsureMembershipIntegrationTest {

    private static final Long PLAYER_ID = 101L;
    private static final Long ORG_ID = 202L;

    @Autowired
    private OrganizationService organizationService;

    /** exists チェックだけ差し替えて TOCTOU の窓を決定論的に再現するためスパイ化する */
    @MockitoSpyBean
    private PlayerOrganizationRepository playerOrganizationRepository;

    @Autowired
    private PushNotificationPreferenceRepository pushNotificationPreferenceRepository;

    @Autowired
    private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate callerTransaction;

    @BeforeEach
    void setUp() {
        callerTransaction = new TransactionTemplate(transactionManager);
        playerOrganizationRepository.deleteAll();
        pushNotificationPreferenceRepository.deleteAll();
        lineNotificationPreferenceRepository.deleteAll();
    }

    @Test
    @DisplayName("TOCTOU競合: existsチェック後に他リクエストが同一ペアを登録済みでも、呼び出し元Txが正常にコミットされる（Issue #1037 回帰）")
    void ensure_concurrentDuplicate_callerTransactionCommitsWithoutRollback() {
        // 別リクエストが先に登録を完了した状態（コミット済み）
        playerOrganizationRepository.save(PlayerOrganization.builder()
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .build());
        // exists チェックだけ false を返し、「チェック通過後に割り込まれた」TOCTOU の窓を再現
        doReturn(false).when(playerOrganizationRepository)
                .existsByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID);

        // 呼び出し元の業務トランザクション（練習参加登録等）を模す。
        // 修正前: 一意制約違反の握りつぶしで rollback-only 化し、コミット時に UnexpectedRollbackException
        assertThatCode(() -> callerTransaction.executeWithoutResult(status ->
                organizationService.ensurePlayerBelongsToOrganization(PLAYER_ID, ORG_ID)
        )).doesNotThrowAnyException();

        // 行は先着リクエストの1件のまま（二重登録なし）
        List<PlayerOrganization> rows = playerOrganizationRepository.findByPlayerId(PLAYER_ID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("新規所属: player_organizations へ挿入され、push/LINE 通知設定のデフォルトレコードも作成される")
    void ensure_newMembership_insertsRowAndDefaultPreferences() {
        callerTransaction.executeWithoutResult(status ->
                organizationService.ensurePlayerBelongsToOrganization(PLAYER_ID, ORG_ID));

        List<PlayerOrganization> rows = playerOrganizationRepository.findByPlayerId(PLAYER_ID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(rows.get(0).getCreatedAt()).isNotNull();
        assertThat(pushNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID)).isPresent();
        assertThat(lineNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID)).isPresent();
    }

    @Test
    @DisplayName("所属済み: 何も追加されない（no-op）")
    void ensure_alreadyMember_noOp() {
        playerOrganizationRepository.save(PlayerOrganization.builder()
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .build());

        callerTransaction.executeWithoutResult(status ->
                organizationService.ensurePlayerBelongsToOrganization(PLAYER_ID, ORG_ID));

        assertThat(playerOrganizationRepository.findByPlayerId(PLAYER_ID)).hasSize(1);
        assertThat(pushNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID)).isEmpty();
        assertThat(lineNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID)).isEmpty();
    }
}
