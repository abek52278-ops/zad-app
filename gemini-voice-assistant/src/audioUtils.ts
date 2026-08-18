/**
 * Audio processing utilities for PCM 16kHz / 24kHz streaming with Gemini Live API
 */

export class AudioUtils {
  /**
   * Convert Buffer / Uint8Array of raw PCM audio to Base64 string
   */
  public static pcmToBase64(pcmBuffer: Buffer | Uint8Array): string {
    return Buffer.from(pcmBuffer).toString('base64');
  }

  /**
   * Convert Base64 string from Gemini Live response back to raw PCM Buffer
   */
  public static base64ToPcm(base64Str: string): Buffer {
    return Buffer.from(base64Str, 'base64');
  }

  /**
   * Wrap raw PCM audio chunk into Gemini Multimodal Live Realtime Input payload format
   */
  public static formatRealtimeAudioChunk(pcmBase64: string, mimeType = 'audio/pcm;rate=16000'): object {
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
  public static formatToolResponse(callId: string, response: object): object {
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
