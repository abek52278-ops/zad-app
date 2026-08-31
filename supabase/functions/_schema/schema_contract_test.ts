// بند 30.1 — عقد بين كود الإيدج فنكشنز وسكيما الداتابيز.
//
// ليه الملف ده موجود؟ مسح 2026-08-31 لقى **نمط عطل منهجي** مش باج واحد: كود
// بيسأل PostgREST عن أعمدة مش موجودة، والنتيجة بترجع في `const { data } = …`
// من غير `error`، فالخطأ بيتبلع والأداة بتقول للعميل "سجّلتلك" وهي مسجّلتش.
// تلات حالات مؤكَّدة وقتها:
//
//   zad-brain/index.ts:4567  zad_inventory.select("id, name, ... updated_at")
//                            → العمود اسمه item_name و updated_at مش موجود.
//                            "حلم الليل" بتاع إعادة التموين ميت من يوم ما اتكتب.
//   zad-brain/index.ts:1032  zad_obligations.select("name,...,is_paid")
//                            .eq("is_paid", false) → is_paid مش موجود أصلاً.
//                            salary_plan بتحسب الالتزامات صفر دايماً.
//   verify-purchase          zad_users.{tier, subscription_status,
//                            subscription_expires_at} + جدول subscriptions —
//                            ولا واحد منهم موجود.
//
// كل واحدة اتصلحت لوحدها. الملف ده بيمنع الجاية: أي `.from("x").select("y")`
// بعمود مش موجود بيفشّل البناء بدل ما يعدي ويفشل بصمت في الإنتاج.
//
// اللقطة: schema.json — مسحوبة من information_schema على المشروع الحي.
// لما تضيف ميجريشن بتغيّر أعمدة، حدّث اللقطة (شوف README.md في نفس الفولدر).

import { assertEquals } from "jsr:@std/assert@1";

const SCHEMA: Record<string, string[]> = JSON.parse(
  await Deno.readTextFile(new URL("./schema.json", import.meta.url)),
);
delete (SCHEMA as Record<string, unknown>)._meta as unknown;

const FUNCTIONS_DIR = new URL("../", import.meta.url).pathname;

/** أعمدة PostgREST الافتراضية اللي مش أعمدة جدول */
const PSEUDO_COLUMNS = new Set(["*", "count"]);

/**
 * جداول مقصود إنها مش في اللقطة — أسماء بتتبني وقت التشغيل أو جداول نظام.
 * لو ضفت اسم هنا، اكتب السبب: القايمة دي هي بالظبط الباب اللي الباجات
 * بتدخل منه لو اتوسّعت من غير تفكير.
 */
const IGNORED_TABLES = new Set<string>([]);

/** بيشيل التعليقات عشان `.from("x")` جوه تعليق ميتحسبش نداء حقيقي */
function stripComments(src: string): string {
  let out = "";
  let i = 0;
  let mode: "code" | "line" | "block" | "str" = "code";
  let quote = "";
  while (i < src.length) {
    const c = src[i], n = src[i + 1];
    if (mode === "code") {
      if (c === "/" && n === "/") { mode = "line"; i += 2; continue; }
      if (c === "/" && n === "*") { mode = "block"; i += 2; continue; }
      if (c === '"' || c === "'" || c === "`") { mode = "str"; quote = c; out += c; i++; continue; }
      out += c; i++; continue;
    }
    if (mode === "line") {
      if (c === "\n") { mode = "code"; out += "\n"; }
      i++; continue;
    }
    if (mode === "block") {
      if (c === "*" && n === "/") { mode = "code"; i += 2; continue; }
      if (c === "\n") out += "\n";
      i++; continue;
    }
    // داخل نص
    if (c === "\\") { out += c + (n ?? ""); i += 2; continue; }
    out += c;
    if (c === quote) mode = "code";
    i++;
  }
  return out;
}

/** بيجيب محتوى `{...}` مبتدي من قوس مفتوح، بمطابقة أقواس */
function braceBody(src: string, openIdx: number): string | null {
  let depth = 0;
  for (let i = openIdx; i < src.length && i < openIdx + 4000; i++) {
    if (src[i] === "{") depth++;
    else if (src[i] === "}") {
      depth--;
      if (depth === 0) return src.slice(openIdx + 1, i);
    }
  }
  return null;
}

/** مفاتيح المستوى الأول من جسم أوبچكت حرفي */
function topLevelKeys(body: string): string[] {
  const keys: string[] = [];
  let depth = 0, i = 0, atKeyPos = true;
  while (i < body.length) {
    const c = body[i];
    if (c === "{" || c === "[" || c === "(") { depth++; i++; atKeyPos = false; continue; }
    if (c === "}" || c === "]" || c === ")") { depth--; i++; continue; }
    if (c === ",") { if (depth === 0) atKeyPos = true; i++; continue; }
    if (depth === 0 && atKeyPos) {
      const m = /^\s*(?:"([A-Za-z_][A-Za-z0-9_]*)"|'([A-Za-z_][A-Za-z0-9_]*)'|([A-Za-z_][A-Za-z0-9_]*))\s*:/
        .exec(body.slice(i));
      if (m) { keys.push(m[1] ?? m[2] ?? m[3]); i += m[0].length; atKeyPos = false; continue; }
      // spread أو مفتاح محسوب — مش قادرين نتحقق، بنعدي
      if (/^\s*\.\.\./.test(body.slice(i))) { atKeyPos = false; }
    }
    i++;
  }
  return keys;
}

/** بيطلع أسماء الأعمدة من نص select بتاع PostgREST */
function selectColumns(sel: string): string[] {
  const out: string[] = [];
  let depth = 0, cur = "";
  const flush = () => {
    let t = cur.trim(); cur = "";
    if (!t) return;
    if (t.includes("(")) return;            // embed لجدول تاني — مش عمود
    if (t.includes("!")) return;            // تلميح علاقة
    if (t.includes(":")) t = t.split(":").pop()!.trim(); // alias:column
    t = t.split("->")[0].split("::")[0].trim();          // JSON path / cast
    t = t.replace(/\.(asc|desc)$/, "");
    if (!t || PSEUDO_COLUMNS.has(t)) return;
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(t)) return;
    out.push(t);
  };
  for (const ch of sel) {
    if (ch === "(") depth++;
    if (ch === ")") depth--;
    if (ch === "," && depth === 0) { flush(); continue; }
    cur += ch;
  }
  flush();
  return out;
}

interface Violation { file: string; table: string; column: string; kind: string; }

function scanFile(path: string, src: string): Violation[] {
  const code = stripComments(src);
  const violations: Violation[] = [];
  const fromRe = /\.from\(\s*["'`]([a-zA-Z_][a-zA-Z0-9_]*)["'`]\s*\)/g;

  const hits: Array<{ table: string; start: number }> = [];
  let m: RegExpExecArray | null;
  while ((m = fromRe.exec(code)) !== null) hits.push({ table: m[1], start: m.index + m[0].length });

  for (let h = 0; h < hits.length; h++) {
    const { table, start } = hits[h];
    if (IGNORED_TABLES.has(table)) continue;

    const known = SCHEMA[table];
    if (!known) {
      violations.push({ file: path, table, column: "—", kind: "جدول مش موجود" });
      continue;
    }
    // نطاق السلسلة: لحد نداء .from التالي أو 2500 حرف
    const end = h + 1 < hits.length ? hits[h + 1].start : Math.min(code.length, start + 2500);
    const scope = code.slice(start, end);
    const cols = new Set(known);

    const check = (col: string, kind: string) => {
      if (!cols.has(col)) violations.push({ file: path, table, column: col, kind });
    };

    // .select("a,b,c")
    for (const s of scope.matchAll(/\.select\(\s*["'`]([^"'`]*)["'`]/g)) {
      for (const c of selectColumns(s[1])) check(c, "select");
    }
    // .eq("col", …) وأخواتها
    const FILTERS = "eq|neq|gt|gte|lt|lte|like|ilike|is|in|contains|containedBy|overlaps|order|not";
    for (const f of scope.matchAll(new RegExp(`\\.(${FILTERS})\\(\\s*["'\`]([a-zA-Z_][a-zA-Z0-9_]*)["'\`]`, "g"))) {
      check(f[2], `.${f[1]}()`);
    }
    // .insert({…}) / .update({…}) / .upsert({…})
    for (const w of scope.matchAll(/\.(insert|update|upsert)\(\s*\{/g)) {
      const openIdx = start + w.index! + w[0].length - 1;
      const body = braceBody(code, openIdx);
      if (!body) continue;
      for (const k of topLevelKeys(body)) check(k, `.${w[1]}()`);
    }
    // onConflict: "a,b" — لازم أعمدة حقيقية كمان
    for (const oc of scope.matchAll(/onConflict\s*:\s*["'`]([^"'`]+)["'`]/g)) {
      for (const c of oc[1].split(",").map((x) => x.trim())) if (c) check(c, "onConflict");
    }
  }
  return violations;
}

async function* tsFiles(dir: string): AsyncGenerator<string> {
  for await (const e of Deno.readDir(dir)) {
    const p = `${dir}/${e.name}`;
    if (e.isDirectory) { yield* tsFiles(p); continue; }
    if (!e.name.endsWith(".ts")) continue;
    if (e.name.endsWith("_test.ts") || e.name.endsWith(".test.ts")) continue;
    yield p;
  }
}

Deno.test("عقد السكيما: كل .from().select()/.eq()/.insert() بيشاور على أعمدة موجودة فعلاً", async () => {
  const all: Violation[] = [];
  for await (const path of tsFiles(FUNCTIONS_DIR.replace(/\/$/, ""))) {
    if (path.includes("/_schema/")) continue;
    all.push(...scanFile(path.replace(FUNCTIONS_DIR, ""), await Deno.readTextFile(path)));
  }

  if (all.length > 0) {
    const lines = all.map((v) =>
      `  ${v.file}\n    ${v.table}.${v.column}  (${v.kind})  ← مش موجود في schema.json`
    );
    throw new Error(
      `\n\n${all.length} مرجع لعمود/جدول مش موجود:\n\n${lines.join("\n")}\n\n` +
      `الأخطاء دي بترجع 42703/42P01 من PostgREST، وبتتبلع بصمت لو الكود مش بيفحص error.\n` +
      `صلّح الكود، أو لو أضفت ميجريشن جديدة حدّث supabase/functions/_schema/schema.json.\n`,
    );
  }
  assertEquals(all.length, 0);
});
