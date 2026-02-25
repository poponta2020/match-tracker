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

# 実行ステージ: 軽量なJREイメージを使用
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ビルドステージからJARファイルをコピー
COPY --from=builder /app/build/libs/*.jar app.jar

# アプリケーション用のユーザーを作成
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# アプリケーションを起動
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]

# Render は環境変数 PORT でポート番号を指定するため、EXPOSE は不要
# アプリケーションは application.properties の server.port=${PORT:8080} で自動的に対応
