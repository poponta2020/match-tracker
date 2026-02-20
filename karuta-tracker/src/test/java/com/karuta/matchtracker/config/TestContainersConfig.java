package com.karuta.matchtracker.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers設定
 * 全てのリポジトリ統合テストで使用するPostgreSQLコンテナを定義
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    /**
     * PostgreSQLコンテナの作成
     * Spring Boot 3.1+の@ServiceConnectionにより、自動的にDataSourceが設定される
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);  // コンテナを再利用してテスト実行を高速化
    }
}
