import { describe, expect, it } from "vitest";
import { formatJstParts, parseIsoToJstParts } from "./datetime.js";

describe("parseIsoToJstParts", () => {
  it("converts a +09:00 offset datetime to matching JST parts", () => {
    const parts = parseIsoToJstParts("2026-07-18T08:00:00+09:00");
    expect(parts).toEqual({ year: 2026, month: 7, day: 18, hour: 8, minute: 0 });
  });

  it("converts a UTC (Z) datetime, rolling the date forward across midnight", () => {
    // 2026-07-17T23:00:00Z = 2026-07-18T08:00:00+09:00
    const parts = parseIsoToJstParts("2026-07-17T23:00:00Z");
    expect(parts).toEqual({ year: 2026, month: 7, day: 18, hour: 8, minute: 0 });
  });

  it("rolls the JST date backward when the source offset is ahead of JST near midnight", () => {
    // 2026-07-18T00:30:00+10:00 = 2026-07-17T14:30:00Z = 2026-07-17T23:30:00+09:00
    const parts = parseIsoToJstParts("2026-07-18T00:30:00+10:00");
    expect(parts).toEqual({ year: 2026, month: 7, day: 17, hour: 23, minute: 30 });
  });

  it("handles year rollover at JST New Year's midnight", () => {
    // 2025-12-31T15:00:00Z = 2026-01-01T00:00:00+09:00
    const parts = parseIsoToJstParts("2025-12-31T15:00:00Z");
    expect(parts).toEqual({ year: 2026, month: 1, day: 1, hour: 0, minute: 0 });
  });

  it("throws on an invalid datetime string", () => {
    expect(() => parseIsoToJstParts("not-a-date")).toThrow(/invalid ISO8601/);
  });
});

describe("formatJstParts", () => {
  it("zero-pads month/day/hour/minute", () => {
    const result = formatJstParts({ year: 2026, month: 7, day: 5, hour: 8, minute: 0 });
    expect(result).toEqual({ dateText: "2026/07/05", timeText: "08:00" });
  });
});
