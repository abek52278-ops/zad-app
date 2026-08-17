---
name: zad-edge-functions
description: >-
  Run type-checks, unit tests, and validation for Supabase Edge Functions (zad-brain, zad-core-intelligence, zad-telegram-bot) in Deno. Use whenever modifying backend functions, AI tool schemas, failover logic, or database migrations.
---

# Zad Edge Functions Development & Testing

This skill provides testing and verification workflows for Zad's Supabase Deno Edge Functions.

## 🚀 Quick Runbook

### 1. Run all Edge Function test suites
Execute from repo root:
```bash
# 1. zad-brain
cd supabase/functions/zad-brain && deno check index.ts callModel.ts validators.ts shared.ts audit.ts && deno test --allow-all

# 2. zad-core-intelligence
cd ../zad-core-intelligence && deno check index.ts redact.ts && deno test --allow-all

# 3. zad-telegram-bot
cd ../zad-telegram-bot && deno test --allow-all
```

### 2. Common Patterns & Invariants
- **Tool validation**: Every mutating tool in `zad-brain/validators.ts` must have bounds checking and unit tests in `validators_test.ts`.
- **Failover**: Model calling logic in `callModel.ts` must support switching across API keys and fallback to Groq on failure without crashing.
- **Privacy**: Check `redactForLog` on any payload containing potential images, tokens, or PII before console logging.
