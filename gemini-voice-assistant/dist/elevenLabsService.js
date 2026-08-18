import { ElevenLabsClient } from 'elevenlabs';
import { exec } from 'child_process';
import { promisify } from 'util';
import fs from 'fs';
import path from 'path';
import os from 'os';
const execAsync = promisify(exec);
export const FEMALE_VOICES = {
    ZADA_AI: '21m00Tcm4TlvDq8ikWAM', // Rachel
    SARAH_STUDIO: 'EXAVITQu4vr4xnSDxMaL', // Sarah
    SALMA_NEURAL: 'ar-EG-SalmaNeural', // Salma Egyptian Female
    ZARIYAH_NEURAL: 'ar-SA-ZariyahNeural' // Zariyah Saudi Female
};
export class ElevenLabsService {
    client;
    isAvailable = false;
    constructor(apiKey) {
        const key = apiKey || process.env.ELEVENLABS_API_KEY || 'sk_53259a25a6218b4fbd842f504cc35100e51a7a9a3f95876a';
        this.client = new ElevenLabsClient({ apiKey: key });
        this.isAvailable = !!key;
    }
    /**
     * Convert text to ultra-realistic female voice (ElevenLabs with Neural Edge fallback)
     */
    async generateSpeech(text, voiceId = process.env.ELEVENLABS_VOICE_ID || FEMALE_VOICES.ZADA_AI, modelId = 'eleven_multilingual_v2') {
        if (!text.trim())
            return null;
        // 1. Try ElevenLabs First
        if (this.isAvailable) {
            try {
                console.log(`[VoiceService] 🎙️ ElevenLabs synthesizing for (${text.length} chars) with voice ${voiceId}...`);
                const audioStream = await this.client.textToSpeech.convert(voiceId, {
                    text: text.trim(),
                    model_id: modelId,
                    output_format: 'mp3_44100_128',
                    voice_settings: {
                        stability: 0.72,
                        similarity_boost: 0.85,
                        style: 0.22,
                        use_speaker_boost: true
                    }
                });
                const chunks = [];
                for await (const chunk of audioStream) {
                    chunks.push(chunk);
                }
                return Buffer.concat(chunks);
            }
            catch (err) {
                console.warn(`[VoiceService] ⚠️ ElevenLabs returned (${err?.statusCode || err?.message || 'Error'}). Falling back to Neural Studio Female Voice...`);
            }
        }
        // 2. Resilient Neural Female Voice Fallback (Salma / Zariyah Neural Studio)
        try {
            console.log(`[VoiceService] 👩 Synthesizing ultra-clear Arabic Female voice via Neural Engine (ar-EG-SalmaNeural)...`);
            const tmpFile = path.join(os.tmpdir(), `zada_voice_${Date.now()}_${Math.random().toString(36).slice(2)}.mp3`);
            const escapedText = text.replace(/"/g, '\\"').replace(/\$/g, '\\$');
            await execAsync(`edge-tts --voice ar-EG-SalmaNeural --rate=+3% --pitch=+2Hz --text "${escapedText}" --write-media "${tmpFile}"`);
            if (fs.existsSync(tmpFile)) {
                const audioBuffer = fs.readFileSync(tmpFile);
                try {
                    fs.unlinkSync(tmpFile);
                }
                catch { }
                return audioBuffer;
            }
        }
        catch (fallbackErr) {
            console.error('[VoiceService] Fallback speech synthesis error:', fallbackErr?.message || fallbackErr);
        }
        return null;
    }
}
