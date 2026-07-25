// Task 16.3 — retry with backoff. Separate module (no Deno.serve) so tests can inject
// a fake fetch instead of hitting the real Anthropic API.

export interface RetryOptions {
  apiKey: string;
  fetchFn?: typeof fetch;
  sleepFn?: (ms: number) => Promise<void>;
  maxAttempts?: number; // default 3 (initial + 2 retries)
}

export async function callClaudeWithRetry(body: unknown, opts: RetryOptions, attempt = 0): Promise<any> {
  const fetchFn = opts.fetchFn ?? fetch;
  const sleepFn = opts.sleepFn ?? ((ms: number) => new Promise((r) => setTimeout(r, ms)));
  const maxAttempts = opts.maxAttempts ?? 3;

  const res = await fetchFn("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": opts.apiKey,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify(body),
  });

  if (res.ok) return await res.json();

  // 400/401/403 محتاجين تصليح إعداد، مش إعادة محاولة — إعادة المحاولة هتخبي الخطأ
  if (res.status === 400 || res.status === 401 || res.status === 403) {
    throw new Error(`config error ${res.status}: ${await res.text()}`);
  }

  if (attempt >= maxAttempts - 1) throw new Error(`exhausted retries: ${res.status}`);

  const retryAfter = Number(res.headers.get("retry-after")) * 1000;
  const backoff = retryAfter || 1000 * Math.pow(4, attempt);
  const jitter = Math.random() * 500;
  await sleepFn(backoff + jitter);
  return callClaudeWithRetry(body, opts, attempt + 1);
}
