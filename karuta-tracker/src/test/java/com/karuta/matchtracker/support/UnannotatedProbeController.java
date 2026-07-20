package com.karuta.matchtracker.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * deny by default の構造テスト用プローブコントローラ（テスト専用・AC-9）
 *
 * <p><b>意図的に {@code @RequireRole} を付けていない。</b>
 * 「アノテーションを付け忘れた新規エンドポイント」を再現し、それでも認証必須になることを
 * {@code AuthenticationIntegrationTest} が検証する。
 *
 * <p>既存エンドポイントを個別に列挙するテストでは、<b>将来</b>追加される付け忘れを検出できない。
 * このプローブは「アノテーション無しでも塞がる」という構造そのものを固定する。
 *
 * <p>src/test 配下にあるため本番のアプリケーションには含まれない。
 */
@RestController
@RequestMapping("/api/__deny-by-default-probe")
public class UnannotatedProbeController {

    @GetMapping
    public String read() {
        return "probe-read";
    }

    @PostMapping
    public String write() {
        return "probe-write";
    }
}
