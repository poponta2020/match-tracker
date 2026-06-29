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

# 実行ステージ: JRE イメージを使用
# glibc ベース(jammy)を採用。Alpine(musl libc)は JVM のスレッド多数・ネイティブ確保の多い
# ワークロードで RSS が膨らみ・OS へ解放されにくく、コンテナOOM(512Mi)を誘発していた(Issue #953)。
# glibc + MALLOC_ARENA_MAX=2 でアリーナ断片化を抑え、ネイティブメモリ常駐を縮小する。
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# ビルドステージからJARファイルをコピー（退避済みの単一ファイル）
COPY --from=builder /app/app.jar app.jar

# アプリケーション用のユーザーを作成（Debian/Ubuntu ベースのため groupadd/useradd を使用）
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# glibc の malloc アリーナ数を制限し、スレッドごとのアリーナ確保による
# ネイティブメモリ断片化/肥大を抑制する（Issue #953）
ENV MALLOC_ARENA_MAX=2

# アプリケーションを起動
# メモリ関連フラグ（コンテナ上限 512Mi）。NMT 計測(Issue #953)で常駐の内訳を確定した結果に基づく:
#   起動直後で既に RSS ~449MB（Heap commit 189MB だが実使用~113・クラス 21,689個=メタ~103MB・
#   Code 43→70・Symbol 35 等）。リークではなく「肥大した正規 footprint が 512 の天井に達している」のが
#   OOM の根本原因。→ ヒープを実測(使用~113MB)に右サイズ化し、過剰コミット分(~40MB)を回収する。
#   -Xmx150m                          ヒープ上限（実使用~113MBに右サイズ化。旧200mは189MBコミット/未使用~75MB）
#   -XX:ReservedCodeCacheSize=64m     コードキャッシュ上限（5hで~70MBへ伸びる尾を抑制。JIT flush で吸収）
#   -XX:MaxMetaspaceSize=192m         メタスペース上限（実~103MBに対する暴走ガード）
#   -XX:MaxDirectMemorySize=64m       NIO ダイレクトバッファ上限（実~0）
# 計測用の -XX:NativeMemoryTracking=summary と NMT ログ出力は、内訳確定につき撤去（オーバーヘッド削減）。
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-Xmx150m", \
  "-XX:ReservedCodeCacheSize=64m", \
  "-XX:MaxMetaspaceSize=192m", \
  "-XX:MaxDirectMemorySize=64m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]

# Render は環境変数 PORT でポート番号を指定するため、EXPOSE は不要
# アプリケーションは application.properties の server.port=${PORT:8080} で自動的に対応
