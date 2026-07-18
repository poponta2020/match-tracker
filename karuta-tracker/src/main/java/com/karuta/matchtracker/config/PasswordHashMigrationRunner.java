package com.karuta.matchtracker.config;

import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 平文パスワードを BCrypt ハッシュへ一括変換する起動時ランナー（auth-tokenization）
 *
 * 移行前の会員は players.password に平文が入っている。ログイン照合を BCrypt に切り替えると
 * そのままではログインできなくなるため、起動時に一度だけ変換する。
 *
 * <ul>
 *   <li><b>冪等</b>: 既に BCrypt 形式（{@code $2a$} / {@code $2b$} / {@code $2y$} で始まる）の
 *       行は触らない。再起動しても二重ハッシュ化は起きない</li>
 *   <li><b>all-or-nothing</b>: 単一トランザクションで実行し、途中で失敗したら全体をロールバックして
 *       起動を失敗させる。一部だけ移行された半端な状態で稼働させないため</li>
 *   <li>論理削除済みの選手も対象にする（DB に平文を残さないため）</li>
 * </ul>
 *
 * 移行完了後もこのランナーは残す。新しい環境（他団体への配布・ステージング）で平文の
 * 初期データから立ち上げる場合に同じ保証が要るため。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PasswordHashMigrationRunner implements ApplicationRunner {

    /** BCrypt ハッシュの接頭辞。これにマッチする値は変換済みとみなす */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.*");

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Player> toMigrate = new ArrayList<>();

        for (Player player : playerRepository.findAll()) {
            String password = player.getPassword();
            if (password != null && !BCRYPT_PATTERN.matcher(password).matches()) {
                toMigrate.add(player);
            }
        }

        if (toMigrate.isEmpty()) {
            log.info("Password hash migration: no plaintext passwords found (already migrated)");
            return;
        }

        for (Player player : toMigrate) {
            player.setPassword(passwordEncoder.encode(player.getPassword()));
        }
        // flush まで行う。トランザクション内で書き込みを確定させ、失敗をこのランナーの
        // トランザクションの中で顕在化させるため（all-or-nothing ＋ 起動失敗）
        playerRepository.saveAllAndFlush(toMigrate);

        log.info("Password hash migration: converted {} plaintext password(s) to BCrypt", toMigrate.size());
    }
}
