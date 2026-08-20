export type UserTokenVerifier = (token: string) => Promise<string | null>;

function bearerToken(req: Request): string {
  const header = req.headers.get("Authorization") ?? "";
  return header.toLowerCase().startsWith("bearer ")
    ? header.slice(7).trim()
    : "";
}

/** True only when both the configured secret and the presented value are non-empty. */
export function hasConfiguredSecret(
  presented: string | null,
  expected: string,
): boolean {
  return expected.length > 0 && presented !== null && presented.length > 0 &&
    presented === expected;
}

export function hasServiceRoleAuthorization(
  req: Request,
  serviceRoleKey: string,
): boolean {
  return hasConfiguredSecret(bearerToken(req), serviceRoleKey);
}

/**
 * Resolves the user identity without trusting a client-supplied user_id.
 * Service-role callers may select a user because they already have unrestricted DB access.
 */
export async function resolveAuthedUserId(
  req: Request,
  body: unknown,
  serviceRoleKey: string,
  verifyUserToken: UserTokenVerifier,
): Promise<string | null> {
  const token = bearerToken(req);
  if (!token) return null;

  if (hasConfiguredSecret(token, serviceRoleKey)) {
    const claimed =
      typeof body === "object" && body !== null && "user_id" in body
        ? (body as { user_id?: unknown }).user_id
        : null;
    return typeof claimed === "string" && claimed.trim().length > 0
      ? claimed.trim()
      : null;
  }

  try {
    return await verifyUserToken(token);
  } catch {
    return null;
  }
}
