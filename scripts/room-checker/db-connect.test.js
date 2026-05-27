/**
 * connectWithRetry の単体テスト。
 * pg への実接続はせず、clientFactory に fake を注入して挙動を検証する。
 *
 * 実行: cd scripts/room-checker && npm test
 */
const test = require("node:test");
const assert = require("node:assert/strict");

const { connectWithRetry } = require("./db-connect");

/** connect() を script で制御可能な fake client を返す factory。 */
function makeFactory(script) {
  const created = [];
  let i = 0;
  const factory = () => {
    const step = script[i++];
    if (!step) {
      throw new Error(`fake factory called more times than script entries (${script.length})`);
    }
    const client = {
      connectCalled: false,
      endCalled: false,
      async connect() {
        this.connectCalled = true;
        if (step.fail) {
          throw new Error(step.fail);
        }
      },
      async end() {
        this.endCalled = true;
        if (step.endError) {
          throw new Error(step.endError);
        }
      },
    };
    created.push(client);
    return client;
  };
  return { factory, created };
}

test("connectWithRetry: 初回成功で1回だけ呼ばれる", async () => {
  const { factory, created } = makeFactory([{ fail: null }]);

  const client = await connectWithRetry("conn", {
    clientFactory: factory,
    baseDelayMs: 1, // テストは高速化
  });

  assert.equal(created.length, 1);
  assert.equal(created[0].connectCalled, true);
  assert.equal(created[0].endCalled, false); // 成功 client は end されない
  assert.equal(client, created[0]);
});

test("connectWithRetry: 何度か失敗してから成功するケース", async () => {
  const { factory, created } = makeFactory([
    { fail: "ECONNREFUSED" },
    { fail: "Connection terminated unexpectedly" },
    { fail: null }, // 3回目で成功
  ]);

  const client = await connectWithRetry("conn", {
    clientFactory: factory,
    maxAttempts: 6,
    baseDelayMs: 1,
  });

  assert.equal(created.length, 3);
  // 失敗した2つは end() されている
  assert.equal(created[0].endCalled, true);
  assert.equal(created[1].endCalled, true);
  // 成功した3つ目は end されない (呼び出し側で finally する想定)
  assert.equal(created[2].endCalled, false);
  assert.equal(client, created[2]);
});

test("connectWithRetry: 全試行失敗で最後のエラーを含む Error を throw", async () => {
  const { factory, created } = makeFactory([
    { fail: "first error" },
    { fail: "second error" },
    { fail: "final error" },
  ]);

  await assert.rejects(
    () => connectWithRetry("conn", {
      clientFactory: factory,
      maxAttempts: 3,
      baseDelayMs: 1,
    }),
    (err) =>
      err instanceof Error &&
      err.message.includes("DB接続に3回失敗しました") &&
      err.message.includes("final error")
  );

  assert.equal(created.length, 3);
  // 全 client が end されている
  for (const c of created) {
    assert.equal(c.endCalled, true);
  }
});

test("connectWithRetry: client.end() 自体が throw しても次の試行に進む", async () => {
  const { factory, created } = makeFactory([
    { fail: "first error", endError: "end also failed" },
    { fail: null },
  ]);

  const client = await connectWithRetry("conn", {
    clientFactory: factory,
    maxAttempts: 6,
    baseDelayMs: 1,
  });

  assert.equal(created.length, 2);
  assert.equal(client, created[1]);
});

test("connectWithRetry: maxAttempts=1 ならリトライしない", async () => {
  const { factory, created } = makeFactory([{ fail: "boom" }]);

  await assert.rejects(
    () => connectWithRetry("conn", {
      clientFactory: factory,
      maxAttempts: 1,
      baseDelayMs: 1,
    }),
    /DB接続に1回失敗しました/
  );

  assert.equal(created.length, 1);
});
