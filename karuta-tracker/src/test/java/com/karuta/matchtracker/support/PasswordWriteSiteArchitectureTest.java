package com.karuta.matchtracker.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * パスワードの書き込み経路がハッシュ化を迂回していないことの構造テスト（auth-tokenization / AC-10）
 *
 * <p>本番コード中で {@code password} を書き込んでいる箇所を全部列挙し、渡している値が
 * <b>必ずハッシュ化済み</b>であることを機械的に確認する。
 *
 * <p><b>なぜ往復テストだけでは足りないか</b>: {@code AuthPasswordIntegrationTest} の
 * AC-11b 往復テストは「その経路が今ハッシュ化していること」を確かめるが、
 * 呼び出し側が将来ヘルパーを経由しなくなる退行（例: {@code registerAndSync} が
 * {@code buildAutoRegisteredPlayer} を使わず自前で {@code Player.builder().password(...)}
 * を書く）を検出できない。書き込み地点そのものを走査することでその穴を塞ぐ。
 *
 * <p>パスワードの書き込み経路を1つでも漏らすと、該当ユーザーは出荷後にログイン不能になる
 * （移行ランナーが次回再起動で拾うため「再起動したら直る」断続バグに化ける）。
 */
@DisplayName("パスワード書き込み経路の構造テスト")
class PasswordWriteSiteArchitectureTest {

    /** `.password(...)` または `setPassword(...)` の引数を捕まえる */
    private static final Pattern WRITE_SITE =
            Pattern.compile("(?:\\.password|setPassword)\\(([^;]*?)\\)\\s*[;\\n]");

    /** 許容する値: ハッシュ化済みの変数、またはチョークポイント経由で encode したもの */
    private static final Pattern ALLOWED =
            Pattern.compile("encodedPassword|passwordPolicy\\.encode\\(|passwordEncoder\\.encode\\(");

    @Test
    @DisplayName("本番コードのパスワード書き込みは、すべてハッシュ化済みの値を渡している")
    void allPasswordWriteSitesUseEncodedValue() throws IOException {
        Path mainSource = Paths.get("src/main/java");
        assertThat(Files.isDirectory(mainSource))
                .as("本番ソースディレクトリが見つからない（テストの作業ディレクトリを確認すること）")
                .isTrue();

        List<String> violations = new ArrayList<>();
        int siteCount = 0;

        try (Stream<Path> files = Files.walk(mainSource)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = WRITE_SITE.matcher(source);
                while (matcher.find()) {
                    siteCount++;
                    String argument = matcher.group(1);
                    if (!ALLOWED.matcher(argument).find()) {
                        violations.add(file + " -> password(" + argument.trim() + ")");
                    }
                }
            }
        }

        // 検出数が 0 なら正規表現が壊れている（何も検査せず green になるのを防ぐ）
        assertThat(siteCount)
                .as("パスワード書き込み箇所を1つも検出できていない。正規表現が実装と乖離している可能性がある")
                .isGreaterThanOrEqualTo(4);

        assertThat(violations)
                .as("平文パスワードを書き込んでいる箇所がある。"
                        + "ハッシュ化はサービス層の PasswordEncoder に集約すること")
                .isEmpty();
    }
}
