// Small pure decision functions extracted out of the Deno.serve handler purely so
// Task 16.4's tests #11/#12 don't need a live server or a live database to verify.

/** Task 16.3 partial-run idempotency: skip a new daily run if one in the window already mutated data. */
export function hasRecentMutatingRun(runs: Array<{ mutations: unknown }>): boolean {
  return runs.some((r) => Array.isArray(r.mutations) && r.mutations.length > 0);
}

/**
 * Task 16.3: on exhausted retries, what to do. `chat` never queues silently — the user
 * is waiting right now, so it gets an honest message instead of a generic {queued:true}
 * with no reply. Every other trigger (daily/event) queues for the drain cron.
 */
export function decideOnBrainFailure(trigger: string): { shouldQueue: boolean; status: number; body: Record<string, unknown> } {
  if (trigger === "chat") {
    return { shouldQueue: false, status: 200, body: { message: "زاد مش قادر يفكر دلوقتي، جرب بعد شوية" } };
  }
  return { shouldQueue: true, status: 200, body: { queued: true } };
}
