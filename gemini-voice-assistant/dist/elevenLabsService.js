import { ElevenLabsClient } from 'elevenlabs';
export class ElevenLabsService {
    client;
    isAvailable = false;
    constructor(apiKey) {
        const key = apiKey || process.env.ELEVENLABS_API_KEY || 'sk_53259a25a6218b4fbd842f504cc35100e51a7a9a3f95876a';
        this.client = new ElevenLabsClient({ apiKey: key });
        this.isAvailable = !!key;
    }
    /**
     * Convert text to ultra-realistic human voice MP3 stream/buffer
     */
    async generateSpeech(text, voiceId = process.env.ELEVENLABS_VOICE_ID || 'JBFqnCBsd6RMkjVDRZzb', modelId = 'eleven_multilingual_v2') {
        if (!this.isAvailable || !text.trim())
            return null;
        try {
            console.log(`[ElevenLabsService] Generating voice for (${text.length} chars) using voice ${voiceId}...`);
            const audioStream = await this.client.textToSpeech.convert(voiceId, {
                text: text.trim(),
                model_id: modelId,
                output_format: 'mp3_44100_128',
                voice_settings: {
                    stability: 0.70, // Balanced stability for steady natural tone
                    similarity_boost: 0.85,
                    style: 0.20,
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
            console.error('[ElevenLabsService] Synthesis error:', err?.message || err);
            return null;
        }
    }
}
