package com.karuta.matchtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * パスワードハッシュ化の設定（auth-tokenization）
 *
 * spring-security-crypto の BCrypt 実装のみを使う。
 * spring-boot-starter-security は導入しない（フィルタチェーンが自動で有効化され、
 * 既存の RoleCheckInterceptor による認証と二重になるため）。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
