#!/usr/bin/env node
/**
 * docs 分割（SPECIFICATION/DESIGN → ドメインファイル群）の機械照合スクリプト。
 *
 * usage:
 *   node scripts/docs-migration/check-migration.mjs --mode=coverage [--ref=origin/main]
 *     → 旧ファイルの全見出しが対応表で追跡できるか（欠落・stale 行の検出）
 *   node scripts/docs-migration/check-migration.mjs --mode=presence [--ref=origin/main]
 *     → 対応表どおりに移設先へ見出しが存在するか（移設後に実行）
 *
 * 対応表: docs/features/ai-dev-optimization/migration-map.md
 * 旧ファイルは git の <ref>（既定 origin/main）から読む。分割後の main では
 * 旧版を含むコミット（例: 14fe9798）を --ref で指定して再実行できる。
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const argv = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(a);
    return m ? [m[1], m[2] ?? true] : [a, true];
  })
);
const MODE = argv.mode || "coverage";
const REF = argv.ref || "origin/main";
const MAP_PATH = argv.map || "docs/features/ai-dev-optimization/migration-map.md";
const OLD_PATHS = { SPECIFICATION: "docs/SPECIFICATION.md", DESIGN: "docs/DESIGN.md" };
const ACTIONS = ["移設", "分配", "廃止", "ハブ残置"];

const failures = [];

function gitShow(ref, p) {
  return execFileSync("git", ["show", `${ref}:${p}`], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
}

function headings(md) {
  const out = [];
  let inCode = false;
  md.split(/\r?\n/).forEach((l, i) => {
    if (/^(```|~~~)/.test(l)) { inCode = !inCode; return; }
    if (inCode) return;
    const m = /^(#{1,6})\s+(.+?)\s*$/.exec(l);
    if (m) out.push({ level: m[1].length, text: m[2], line: i + 1 });
  });
  return out;
}

// 章番号（例: "3.2.1 " / "5.3.-1 " / "A. "）を除いた本文で比較する
function normalize(t) {
  return t
    .replace(/^§?\s*/, "")
    .replace(/^[0-9０-９]+(?:[.\-．－][0-9０-９\-－]+)*[.．]?\s+/, "")
    .replace(/^[A-Z][.．]\s*/, "")
    .replace(/\s+/g, " ")
    .trim();
}

function parseMap(md) {
  const rows = [];
  md.split(/\r?\n/).forEach((l, i) => {
    const cells = l.split("|").map((c) => c.trim());
    if (cells.length >= 6 && (cells[1] === "SPECIFICATION" || cells[1] === "DESIGN") && ACTIONS.includes(cells[3])) {
      rows.push({ file: cells[1], old: cells[2], action: cells[3], target: cells[4], newHeading: cells[5] ?? "", line: i + 1 });
    }
  });
  return rows;
}

function expandTargets(g) {
  return g.split(",").map((s) => s.trim()).filter(Boolean).flatMap((one) => {
    const m = /^(.*)\/\*\.md$/.exec(one);
    if (!m) return fs.existsSync(one) ? [one] : [];
    const dir = m[1];
    if (!fs.existsSync(dir)) return [];
    return fs.readdirSync(dir).filter((f) => f.endsWith(".md")).map((f) => path.join(dir, f));
  });
}

function headingMatch(actualNorm, expectedNorm) {
  if (!expectedNorm) return true;
  if (actualNorm === expectedNorm) return true;
  if (actualNorm.includes(expectedNorm)) return true;
  if (expectedNorm.includes(actualNorm) && actualNorm.length >= 4) return true;
  return false;
}

const map = parseMap(fs.readFileSync(MAP_PATH, "utf8"));
if (map.length === 0) {
  console.error(`対応表の行が読めません: ${MAP_PATH}`);
  process.exit(2);
}

if (MODE === "coverage") {
  for (const [key, p] of Object.entries(OLD_PATHS)) {
    const hs = headings(gitShow(REF, p));
    const rowsFor = map.filter((r) => r.file === key);

    for (const r of rowsFor) {
      if (!hs.some((h) => h.text === r.old)) {
        failures.push(`[stale-row] ${key}: 対応表の旧見出しが旧ファイルに存在しない: 「${r.old}」(map:${r.line}行目)`);
      }
    }

    const stack = [];
    for (const h of hs) {
      while (stack.length && stack[stack.length - 1].level >= h.level) stack.pop();
      const row = rowsFor.find((r) => r.old === h.text) || null;
      let covered = !!row;
      if (!covered) {
        for (let j = stack.length - 1; j >= 0; j--) {
          const anc = stack[j];
          if (anc.row) {
            if (anc.row.action === "移設" || anc.row.action === "分配" || anc.row.action === "ハブ残置") covered = true;
            break; // 廃止は継承しない（直近の対応行で判定を打ち切る）
          }
        }
      }
      if (!covered) {
        failures.push(
          h.level <= 3
            ? `[uncovered] ${key} L${h.line}「${"#".repeat(h.level)} ${h.text}」に対応行がなく、継承元（移設/分配/ハブ残置の祖先）もない`
            : `[uncovered-child] ${key} L${h.line}「${h.text}」(レベル${h.level}) の祖先に移設/分配行がない`
        );
      }
      stack.push({ level: h.level, row });
    }
  }
} else if (MODE === "presence") {
  for (const r of map) {
    if (r.action === "ハブ残置" || r.action === "廃止") continue;
    if (r.action === "移設") {
      if (!fs.existsSync(r.target)) {
        failures.push(`[missing-file] ${r.target} が存在しない（map:${r.line}行目「${r.old}」）`);
        continue;
      }
      const expected = normalize(r.newHeading || r.old);
      const ok = headings(fs.readFileSync(r.target, "utf8")).some((h) => headingMatch(normalize(h.text), expected));
      if (!ok) failures.push(`[missing-heading] ${r.target} に「${expected}」相当の見出しがない（map:${r.line}行目「${r.old}」）`);
    }
    if (r.action === "分配") {
      const hs = headings(gitShow(REF, OLD_PATHS[r.file]));
      const idx = hs.findIndex((h) => h.text === r.old);
      if (idx < 0) { failures.push(`[stale-row] 分配元「${r.old}」が旧ファイルにない（map:${r.line}行目）`); continue; }
      const base = hs[idx];
      const children = [];
      for (let j = idx + 1; j < hs.length && hs[j].level > base.level; j++) {
        if (hs[j].level === base.level + 1) children.push(hs[j]);
      }
      const targets = expandTargets(r.target);
      if (targets.length === 0) { failures.push(`[missing-file] 分配先 ${r.target} に該当ファイルがない（map:${r.line}行目）`); continue; }
      const allHeads = targets.flatMap((t) => headings(fs.readFileSync(t, "utf8")).map((h) => normalize(h.text)));
      for (const c of children) {
        const exp = normalize(c.text);
        if (!allHeads.some((n) => headingMatch(n, exp))) {
          failures.push(`[undistributed] 「${c.text}」（${r.file} L${c.line}）が ${r.target} のいずれにも見出しとして存在しない`);
        }
      }
    }
  }
} else {
  console.error(`不明な mode: ${MODE}（coverage | presence）`);
  process.exit(2);
}

if (failures.length) {
  console.log(`\n=== ${MODE} 照合: FAIL（${failures.length}件） ===`);
  for (const f of failures) console.log(`  ${f}`);
  process.exit(1);
} else {
  console.log(`=== ${MODE} 照合: PASS（対応表 ${map.length}行 / ref=${REF}） ===`);
}
