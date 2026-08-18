/**
 * Audio processing utilities for PCM 16kHz / 24kHz streaming with Gemini Live API
 */
export class AudioUtils {
    /**
     * Convert Buffer / Uint8Array of raw PCM audio to Base64 string
     */
    static pcmToBase64(pcmBuffer) {
        return Buffer.from(pcmBuffer).toString('base64');
    }
    /**
     * Convert Base64 string from Gemini Live response back to raw PCM Buffer
     */
    static base64ToPcm(base64Str) {
        return Buffer.from(base64Str, 'base64');
    }
    /**
     * Wrap raw PCM audio chunk into Gemini Multimodal Live Realtime Input payload format
     */
    static formatRealtimeAudioChunk(pcmBase64, mimeType = 'audio/pcm;rate=16000') {
        return {
            realtimeInput: {
                mediaChunks: [
                    {
                        mimeType: mimeType,
                        data: pcmBase64
                    }
                ]
            }
        };
    }
    /**
     * Format Gemini tool response payload to send back over Multimodal Live WebSocket
     */
    static formatToolResponse(callId, response) {
        return {
            toolResponse: {
                functionResponses: [
                    {
                        response: { output: response },
                        id: callId
                    }
                ]
            }
        };
    }
}
