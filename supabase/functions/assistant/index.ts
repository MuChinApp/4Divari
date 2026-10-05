// Supabase Edge Function: assistant
//
// Thin LLM proxy for چاردیواری's decision-support assistant.
// - The provider API key lives ONLY here (supabase secrets) — never in the app.
// - This function never executes tools: the client runs them against real
//   repositories under the caller's RLS session (AI sees only data the user
//   could fetch themselves).
// - Requires an authenticated Supabase session (verify_jwt at the gateway
//   plus an explicit `role == "authenticated"` check).
// - Fails closed with 503 when the provider env is missing — it never
//   fabricates model output.

const corsHeaders: Record<string, string> = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers":
    "authorization, x-client-info, apikey, content-type",
  "access-control-allow-methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      ...corsHeaders,
    },
  });
}

/** Decode the JWT payload WITHOUT re-verifying (the gateway already did). */
function decodeJwtRole(token: string): string {
  try {
    const payload = token.split(".")[1] ?? "";
    const b64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
    const claims = JSON.parse(atob(padded)) as { role?: string };
    return claims.role ?? "";
  } catch {
    return "";
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "");
  if (decodeJwtRole(token) !== "authenticated") {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const providerUrl = Deno.env.get("AI_PROVIDER_URL");
  const providerKey = Deno.env.get("AI_PROVIDER_KEY");
  const model = Deno.env.get("AI_MODEL");
  if (!providerUrl || !providerKey || !model) {
    return jsonResponse({ error: "ai_not_configured" }, 503);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json() as Record<string, unknown>;
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }
  const messages = body.messages;
  if (!Array.isArray(messages) || messages.length === 0) {
    return jsonResponse({ error: "messages_required" }, 400);
  }

  const requestedModel = typeof body.model === "string" && body.model.length > 0
    ? body.model
    : model;
  const tools = Array.isArray(body.tools) && body.tools.length > 0
    ? body.tools
    : undefined;

  let upstream: Response;
  try {
    upstream = await fetch(providerUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${providerKey}`,
      },
      body: JSON.stringify({
        model: requestedModel,
        messages,
        tools,
        temperature: 0.2,
      }),
    });
  } catch (err) {
    console.error("provider_unreachable", err);
    return jsonResponse({ error: "provider_unreachable" }, 502);
  }

  if (!upstream.ok) {
    const detail = await upstream.text().catch(() => "");
    console.error("provider_error", upstream.status, detail.slice(0, 500));
    return jsonResponse({ error: "provider_error" }, 502);
  }

  let data: { choices?: Array<{ message?: unknown }> } | null = null;
  try {
    data = await upstream.json() as
      { choices?: Array<{ message?: unknown }> } | null;
  } catch {
    return jsonResponse({ error: "provider_invalid_json" }, 502);
  }

  const message = data?.choices?.[0]?.message;
  if (!message || typeof message !== "object") {
    return jsonResponse({ error: "provider_empty_response" }, 502);
  }

  return jsonResponse({ message });
});
