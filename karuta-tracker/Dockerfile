# マルチステージビルド: ビルドステージ
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Gradleラッパーと依存関係定義をコピー
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 依存関係を事前にダウンロード（キャッシュ活用）
RUN ./gradlew dependencies --no-daemon || true

# ソースコードをコピー
COPY src src

# アプリケーションをビルド（テストはスキップ）
RUN ./gradlew bootJar --no-daemon -x test

# 生成した JAR を固定パスへ退避し、後続の COPY --from の元を単一ファイルにする
# （bootJar が失敗した場合は JAR が無く cp が失敗してビルドが正しく落ちる）
RUN cp build/libs/*.jar /app/app.jar

# COPY --from 時に BuildKit がビルダのファイルシステムを走査してキャッシュキーを計算する際、
# Gradle キャッシュ内の揮発エントリ（testcontainers 依存 org.rnorth.duct-tape 等）の lstat に
# 失敗して「failed to walk ... no such file or directory」でビルドが落ちる事象を防ぐため、
# Gradle キャッシュを除去して走査対象から外す。bootJar の失敗をマスクしないよう別 RUN とし、
# 除去自体の失敗はビルドを止めない（|| true）。
RUN rm -rf /home/gradle/.gradle/caches "$HOME/.gradle/caches" /root/.gradle/caches 2>/dev/null || true

# 実行ステージ: 軽量なJREイメージを使用
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ビルドステージからJARファイルをコピー（退避済みの単一ファイル）
COPY --from=builder /app/app.jar app.jar

# アプリケーション用のユーザーを作成
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# アプリケーションを起動
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-Xmx200m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]

# Render は環境変数 PORT でポート番号を指定するため、EXPOSE は不要
# アプリケーションは application.properties の server.port=${PORT:8080} で自動的に対応
