package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 認証トークンエンティティ
 *
 * ログイン時にサーバが発行するトークンの状態を保持します。
 * 生トークンは保存せず、SHA-256 ハッシュ（hex 64文字）のみを保存します。
 * DB が漏洩しても保存値からトークンを復元・再利用できないようにするためです。
 *
 * 失効は revoked_at に時刻を入れる論理失効で表現し、行は削除しません。
 */
@Entity
@Table(name = "auth_tokens", indexes = {
    @Index(name = "idx_auth_tokens_player_id", columnList = "player_id"),
    @Index(name = "idx_auth_tokens_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * トークンの持ち主の選手ID
     * 選手単位の一括失効（パスワード変更・論理削除）に使う
     */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /**
     * 生トークンの SHA-256 ハッシュ（hex 64文字）
     * 検索キー。生トークンそのものは保存しない
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * 発行日時
     */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /**
     * 有効期限（発行の約1年後）
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 失効日時。NULL なら有効
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * 指定時刻において有効なトークンかを判定する
     *
     * @param now 判定基準時刻
     * @return 失効しておらず期限内なら true
     */
    public boolean isValidAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
