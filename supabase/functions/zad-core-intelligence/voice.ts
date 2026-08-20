export const VOICE_IDS: Record<string, string> = {
  sarah_warm: "EXAVITQu4vr4xnSDxMaL",
  karim_pro: "pNInz6obpgDQGcFmaJgB",
  pet_mascot: "MF3mGyEYCl7XYWbV9V6O",
};

export interface ValidVoiceRequest {
  text: string;
  voiceId: string;
}

export function bearerToken(req: Request): string {
  const header = req.headers.get("Authorization") ?? "";
  return header.toLowerCase().startsWith("bearer ")
    ? header.slice(7).trim()
    : "";
}

export function validateVoicePayload(payload: unknown): ValidVoiceRequest | null {
  if (!payload || typeof payload !== "object") return null;
  const row = payload as Record<string, unknown>;
  const text = typeof row.text === "string" ? row.text.trim() : "";
  const persona = typeof row.persona === "string" ? row.persona : "";
  const voiceId = VOICE_IDS[persona];
  if (!text || text.length > 1200 || !voiceId) return null;
  return { text, voiceId };
}

export async function requestElevenLabsVoice(
  input: ValidVoiceRequest,
  apiKey: string,
  fetcher: typeof fetch = fetch,
): Promise<Response> {
  if (!apiKey) throw new Error("ELEVENLABS_API_KEY is not configured");
  return await fetcher(
    `https://api.elevenlabs.io/v1/text-to-speech/${input.voiceId}/stream?output_format=pcm_24000`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "xi-api-key": apiKey,
      },
      body: JSON.stringify({
        text: input.text,
        model_id: Deno.env.get("ELEVENLABS_MODEL_ID") ?? "eleven_multilingual_v2",
        voice_settings: {
          stability: 0.42,
          similarity_boost: 0.82,
          style: 0.32,
          use_speaker_boost: true,
        },
      }),
    },
  );
}
