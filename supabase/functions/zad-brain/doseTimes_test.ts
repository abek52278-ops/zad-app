// Guards the dose-schedule normalizer. The regression it exists for is in production
// data, not in theory: on 2026-08-16 this account held "سبروفار" at `02:00,14:00` and
// "اجمانتين" at `01:30,13:30` — the model was told to "compute dose_times from the
// current time" by a prompt that never gives it a clock or a timezone, so it invented an
// anchor in the middle of the night and AlarmManager dutifully rang there every day.

import { assertEquals } from "jsr:@std/assert@1";
import { normalizeDoseTimes } from "./shared.ts";

Deno.test("a night-window schedule the customer never asked for is moved to daytime", () => {
  assertEquals(normalizeDoseTimes("02:00,14:00", 2), "09:00,21:00");
  assertEquals(normalizeDoseTimes("01:30,13:30", 2), "09:00,21:00");
});

Deno.test("a night dose the customer named explicitly is left alone", () => {
  assertEquals(normalizeDoseTimes("02:00,14:00", 2, true), "02:00,14:00");
});

Deno.test("daytime schedules pass through, normalized and sorted", () => {
  assertEquals(normalizeDoseTimes("21:00, 9:00", 2), "09:00,21:00");
  assertEquals(normalizeDoseTimes("08:00,14:00,20:00", 3), "08:00,14:00,20:00");
});

Deno.test("no times given falls back to the anchor for that dose count", () => {
  assertEquals(normalizeDoseTimes(null, 1), "09:00");
  assertEquals(normalizeDoseTimes(undefined, 3), "08:00,14:00,20:00");
  assertEquals(normalizeDoseTimes("", 4), "08:00,13:00,18:00,23:00");
});

Deno.test("malformed entries are dropped, not passed to the phone", () => {
  // LocalTime.parse() on the Android side throws on all three of these.
  assertEquals(normalizeDoseTimes("24:00,08:00", 2), "08:00");
  assertEquals(normalizeDoseTimes("بعد الفطار,20:00", 2), "20:00");
  assertEquals(normalizeDoseTimes("09:70,13:00", 2), "13:00");
});

Deno.test("nothing usable and nothing to derive from returns null", () => {
  assertEquals(normalizeDoseTimes("لما اصحى"), null);
  assertEquals(normalizeDoseTimes(null), null);
});

Deno.test("duplicates collapse so one slot never schedules two alarms", () => {
  assertEquals(normalizeDoseTimes("09:00,9:00,21:00", 2), "09:00,21:00");
});
