import { assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import { pushToTelegram } from "./push.ts";

// pushToTelegram بتكلّم zad-telegram-bot?job=realtime_push — نفس العقد اللي تريجرات
// الداتابيز بتستخدمه. التستات دي بتثبّت العقد ده من غير ما تبعت ولا رسالة حقيقية:
// fetch مزيّف بيسجّل الطلب ويرجّع رد البوت زي ما اتقاس على الإنتاج 2026-09-13
// (`{"ok":true,"delivered":false,"reason":"not linked"}` لمستخدم مش مربوط).

const SECRET = "test-realtime-push-secret";
const BASE = "https://example.supabase.co";

function withEnv<T>(vars: Record<string, string | undefined>, fn: () => Promise<T>): Promise<T> {
  const previous: Record<string, string | undefined> = {};
  for (const [k, v] of Object.entries(vars)) {
    previous[k] = Deno.env.get(k);
    if (v === undefined) Deno.env.delete(k);
    else Deno.env.set(k, v);
  }
  return fn().finally(() => {
    for (const [k, v] of Object.entries(previous)) {
      if (v === undefined) Deno.env.delete(k);
      else Deno.env.set(k, v);
    }
  });
}

function fakeFetch(status: number, body: string, seen: { url?: string; init?: RequestInit }[]): typeof fetch {
  return ((url: string | URL | Request, init?: RequestInit) => {
    seen.push({ url: String(url), init });
    return Promise.resolve(new Response(body, { status }));
  }) as typeof fetch;
}

Deno.test("sends title, body and user_id to realtime_push with the secret header", async () => {
  const seen: { url?: string; init?: RequestInit }[] = [];
  const result = await withEnv({ ZAD_REALTIME_PUSH_SECRET: SECRET, SUPABASE_URL: BASE }, () =>
    pushToTelegram("user-1", "📋 ملخص البيت من زاد", "نص الملخص", fakeFetch(200, '{"ok":true,"delivered":true}', seen))
  );
  assertEquals(result, "delivered");
  assertEquals(seen.length, 1);
  assertEquals(seen[0].url, `${BASE}/functions/v1/zad-telegram-bot?job=realtime_push`);
  assertEquals((seen[0].init?.headers as Record<string, string>)["X-Realtime-Push-Secret"], SECRET);
  const sent = JSON.parse(String(seen[0].init?.body));
  assertEquals(sent, { user_id: "user-1", title: "📋 ملخص البيت من زاد", body: "نص الملخص" });
});

Deno.test("an unlinked user is not a failure", async () => {
  const result = await withEnv({ ZAD_REALTIME_PUSH_SECRET: SECRET, SUPABASE_URL: BASE }, () =>
    pushToTelegram("user-2", "t", "b", fakeFetch(200, '{"ok":true,"delivered":false,"reason":"not linked"}', []))
  );
  assertEquals(result, "not_linked");
});

Deno.test("a rejected secret or a bot error reports failed, not silence", async () => {
  for (const [status, body] of [[401, "unauthorized"], [503, '{"ok":false,"reason":"bot not configured"}'], [500, "boom"]] as const) {
    const result = await withEnv({ ZAD_REALTIME_PUSH_SECRET: SECRET, SUPABASE_URL: BASE }, () =>
      pushToTelegram("user-3", "t", "b", fakeFetch(status, body, []))
    );
    assertEquals(result, "failed", `HTTP ${status}`);
  }
});

Deno.test("a missing secret skips delivery without calling the network", async () => {
  const seen: { url?: string; init?: RequestInit }[] = [];
  const result = await withEnv({ ZAD_REALTIME_PUSH_SECRET: undefined, SUPABASE_URL: BASE }, () =>
    pushToTelegram("user-4", "t", "b", fakeFetch(200, "{}", seen))
  );
  assertEquals(result, "no_secret");
  assertEquals(seen.length, 0);
});

Deno.test("a network error reports failed instead of throwing into the task loop", async () => {
  const throwing = (() => Promise.reject(new Error("network down"))) as typeof fetch;
  const result = await withEnv({ ZAD_REALTIME_PUSH_SECRET: SECRET, SUPABASE_URL: BASE }, () =>
    pushToTelegram("user-5", "t", "b", throwing)
  );
  assertEquals(result, "failed");
});

Deno.test("long bodies are trimmed under Telegram's 4096-character message limit", async () => {
  const seen: { url?: string; init?: RequestInit }[] = [];
  await withEnv({ ZAD_REALTIME_PUSH_SECRET: SECRET, SUPABASE_URL: BASE }, () =>
    pushToTelegram("user-6", "عنوان", "س".repeat(9000), fakeFetch(200, '{"delivered":true}', seen))
  );
  const sent = JSON.parse(String(seen[0].init?.body));
  assertEquals(sent.body.length, 3500);
  assertStringIncludes(sent.title, "عنوان");
});
