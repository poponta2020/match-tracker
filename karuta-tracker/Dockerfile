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
# メモリ関連フラグ（コンテナ上限 512Mi）。ヒープ外で増加しうる主要領域に上限を設け、
# 明示上限の合計をコンテナ上限の内側（200+192+64=456Mi）に収める。これによりメタスペース/
# ダイレクトの暴走を cgroup SIGKILL より手前で Java OOME として顕在化させ MEM-DIAG ログで追える。
# 注: 512Mi は本アプリには元来きつく、コードキャッシュ/スレッドスタック/その他ネイティブ込みでは
# 上限到達前にコンテナ OOM が起きうる（明示上限は万能のガードレールではない）。ネイティブ RSS そのものの
# 縮小は glibc + MALLOC_ARENA_MAX が担い、増加領域の特定は MemoryDiagnosticsLogger（5分ごとの実RSS含む）
# が担う。各上限値はデプロイ後の MEM-DIAG 観測で精緻化する（Issue #953）:
#   -Xmx200m                          ヒープ上限（既存・実績値のため据え置き）
#   -XX:MaxMetaspaceSize=192m         メタスペース上限（暴走ガード。実使用見込みに余裕を持たせた値。ロガーで接近を事前検知）
#   -XX:MaxDirectMemorySize=64m       NIO ダイレクトバッファ上限（未指定時の既定 ≒ -Xmx を抑制）
# 注: NMT(-XX:NativeMemoryTracking=summary) は Render free では jcmd で読み出せず純オーバーヘッド
#     （512Mi 環境では RSS を押し上げる）になるため不採用。ネイティブメモリの増加領域は
#     MemoryDiagnosticsLogger の実RSS/各 MXBean 値から推定する。
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-Xmx200m", \
  "-XX:MaxMetaspaceSize=192m", \
  "-XX:MaxDirectMemorySize=64m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]

# Render は環境変数 PORT でポート番号を指定するため、EXPOSE は不要
# アプリケーションは application.properties の server.port=${PORT:8080} で自動的に対応
