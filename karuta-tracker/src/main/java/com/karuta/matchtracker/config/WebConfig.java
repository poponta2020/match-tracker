package com.karuta.matchtracker.config;

import com.karuta.matchtracker.interceptor.RoleCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RoleCheckInterceptor roleCheckInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 会場予約プロキシ画面 (/view, /fetch/**) は API オリジンから直接表示される
        // フルページ用エンドポイント。SPA からの cross-origin 呼び出しではなく、
        // 表示後の form submit 等の same-origin リクエストとして使われる。
        // 通常 same-origin なら Spring の CORS チェックは素通りするはずだが、Render の
        // ように TLS を edge で終端する reverse proxy 配下だと request.getScheme()/
        // getServerName()/getServerPort() が内部値を返し、Origin ヘッダと一致せず
        // cross-origin と誤判定されることがある (Issue #570)。
        // forward-headers-strategy=framework と併用しつつ defense-in-depth として、
        // これらのパスに任意 Origin を許可するマッピングを /api/** より先に登録する
        // (CorsRegistry は最初に一致したマッピングを使うため、より具体的な
        // パターンを先に登録する必要がある)。
        // /session は SPA から呼ばれるため /api/** 側 (allowedOrigins) のままにする。
        registry.addMapping("/api/venue-reservation-proxy/view")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
        registry.addMapping("/api/venue-reservation-proxy/fetch/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // LINE Webhookは外部サーバーからのリクエストなのでCORS制限なし
        registry.addMapping("/api/line/webhook/**")
                .allowedOrigins("*")
                .allowedMethods("POST")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleCheckInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/line/webhook/**");
    }
}
