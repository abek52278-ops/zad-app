import { assertEquals } from "jsr:@std/assert@1";
import {
  hasConfiguredSecret,
  hasServiceRoleAuthorization,
  resolveAuthedUserId,
} from "./auth.ts";

const request = (token?: string) =>
  new Request("https://example.test", {
    headers: token === undefined ? {} : { Authorization: `Bearer ${token}` },
  });

Deno.test("configured secrets fail closed when the server value is empty", () => {
  assertEquals(hasConfiguredSecret(null, ""), false);
  assertEquals(hasConfiguredSecret("", ""), false);
  assertEquals(hasConfiguredSecret("secret", ""), false);
  assertEquals(hasConfiguredSecret("secret", "secret"), true);
});

Deno.test("service-role authorization requires an exact non-empty bearer token", () => {
  assertEquals(hasServiceRoleAuthorization(request(), "service-secret"), false);
  assertEquals(
    hasServiceRoleAuthorization(request("wrong"), "service-secret"),
    false,
  );
  assertEquals(
    hasServiceRoleAuthorization(request("service-secret"), "service-secret"),
    true,
  );
});

Deno.test("user JWT identity wins over a claimed body user_id", async () => {
  const resolved = await resolveAuthedUserId(
    request("user-jwt"),
    { user_id: "victim-id" },
    "service-secret",
    async (token) => token === "user-jwt" ? "real-user-id" : null,
  );
  assertEquals(resolved, "real-user-id");
});

Deno.test("service role may select a user and public requests are rejected", async () => {
  const verify = async (_token: string) => "must-not-run";
  assertEquals(
    await resolveAuthedUserId(
      request("service-secret"),
      { user_id: "target-id" },
      "service-secret",
      verify,
    ),
    "target-id",
  );
  assertEquals(
    await resolveAuthedUserId(
      request(),
      { user_id: "target-id" },
      "service-secret",
      verify,
    ),
    null,
  );
});

Deno.test("invalid user JWT is rejected", async () => {
  assertEquals(
    await resolveAuthedUserId(
      request("bad-jwt"),
      { user_id: "target-id" },
      "service-secret",
      async () => null,
    ),
    null,
  );
});
