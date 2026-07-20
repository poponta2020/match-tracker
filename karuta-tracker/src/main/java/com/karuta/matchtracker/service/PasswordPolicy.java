package com.karuta.matchtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * パスワードのハッシュ化を一手に引き受けるチョークポイント（auth-tokenization）
 *
 * <p>パスワードを保存する経路（選手作成・選手更新・招待登録・伝助の自動登録）は
 * すべてここを通す。{@code PasswordEncoder} を直接呼ばないこと。
 *
 * <p><b>バイト長を検証する理由</b>: BCrypt は入力を 72 バイトで打ち切る。
 * このとき例外は発生せず<b>黙って切り詰められる</b>ため、73 バイト以上のパスワードは
 * 先頭 72 バイトさえ一致すれば認証が通ってしまう（実測で確認済み）。
 * 日本語は 1 文字 3 バイトなので 25 文字程度で上限に達する。
 * 気づかないうちにパスワードが弱くなるのを防ぐため、上限超過は明示的に弾く。
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** BCrypt が扱える最大バイト長。これを超えた分は黙って切り捨てられる */
    static final int MAX_PASSWORD_BYTES = 72;

    /** 既存仕様の最低文字数 */
    static final int MIN_PASSWORD_LENGTH = 8;

    private final PasswordEncoder passwordEncoder;

    /**
     * 生パスワードを検証し、BCrypt ハッシュを返す
     *
     * @param rawPassword 生パスワード
     * @return BCrypt ハッシュ
     * @throws IllegalArgumentException 未設定・短すぎる・長すぎる場合（400 で返る）
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("パスワードは必須です");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "パスワードは" + MIN_PASSWORD_LENGTH + "文字以上で入力してください");
        }

        int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "パスワードが長すぎます（UTF-8 で " + MAX_PASSWORD_BYTES + "バイト以内。"
                            + "日本語なら24文字程度が上限です）");
        }

        return passwordEncoder.encode(rawPassword);
    }
}
