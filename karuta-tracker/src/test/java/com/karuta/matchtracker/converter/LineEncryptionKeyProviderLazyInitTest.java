package com.karuta.matchtracker.converter;

import com.karuta.matchtracker.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本番プロファイル(render)は {@code spring.main.lazy-initialization=true} を有効にしている。
 * {@link LineEncryptionKeyProvider} はどのビーンからも参照されない「起動時 populate 専用」ビーンのため、
 * グローバル lazy-init 下では {@code @Lazy(false)} が無いとインスタンス化されず {@code @PostConstruct} が
 * 走らない（＝ホルダ空のまま全書き込みが fail-fast する本番限定バグ。PR #1132 出荷後に実発生）。
 *
 * <p>このテストは**その本番条件を再現**し、Provider が eager 初期化されホルダが populate されることを担保する。
 * 誰も Provider を autowire していない点が要点（lazy のままなら未生成 = {@code containsSingleton} が false）。
 */
@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("LineEncryptionKeyProvider lazy-init 下の eager 初期化 回帰テスト")
class LineEncryptionKeyProviderLazyInitTest {

    @Autowired
    private ConfigurableApplicationContext ctx;

    @AfterEach
    void clearHolder() {
        LineEncryptionKeyHolder.clear();
    }

    @Test
    @DisplayName("グローバル lazy-init 有効でも Provider は起動時に生成されホルダを populate する")
    void providerInitializesEagerlyUnderGlobalLazyInitialization() {
        // @Lazy(false) が無いと、参照されない Provider は lazy のまま未生成 → containsSingleton=false。
        assertThat(ctx.getBeanFactory().containsSingleton("lineEncryptionKeyProvider"))
                .as("Provider はグローバル lazy-init 下でも eager にインスタンス化されねばならない")
                .isTrue();

        // eager 初期化の結果、@PostConstruct が走り application-test.properties の鍵でホルダが populate される。
        assertThat(LineEncryptionKeyHolder.current())
                .as("起動時にホルダが populate されていること（コンバータの暗号化書込が通る前提）")
                .isPresent();
    }
}
