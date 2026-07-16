# line-chat-worker: LINE Official Account Manager のチャット予約送信を自動化する常駐ワーカー。
# Oracle Cloud VM（東京・kagetra_new 稼働ホスト）へ配置するが、kagetra_new の
# コンテナ群とはネットワーク・ボリュームを共有しない（docker-compose.yml 参照）。

FROM node:20-bookworm-slim AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
# このステージはTSコンパイルのみで実ブラウザ操作を行わないため Chromium DL は不要。
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:20-bookworm-slim
ENV TZ=Asia/Tokyo
WORKDIR /app

COPY package.json package-lock.json* ./
# 本番イメージは実ブラウザ操作が必要なため Chromium を含めてインストールする
# （CI では PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 でスキップしている点と対照的）。
RUN npm ci --omit=dev \
    && npx playwright install --with-deps chromium

COPY --from=builder /app/dist ./dist

CMD ["node", "dist/index.js"]
