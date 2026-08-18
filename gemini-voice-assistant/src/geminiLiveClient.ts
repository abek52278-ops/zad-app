import WebSocket from 'ws';
import { SYSTEM_PROMPT, DEFAULT_VOICE } from './systemPrompt.js';
import { MCPManager } from './mcpManager.js';
import { AudioUtils } from './audioUtils.js';

export interface GeminiLiveOptions {
  apiKey: string;
  model?: string;
  voiceName?: string;
  mcpManager: MCPManager;
  onAudioData: (pcmBuffer: Buffer) => void;
  onTextData: (text: string) => void;
  onError: (error: Error) => void;
  onClose: () => void;
}

export class GeminiLiveClient {
  private ws: WebSocket | null = null;
  private options: GeminiLiveOptions;
  private isConnected = false;

  constructor(options: GeminiLiveOptions) {
    this.options = options;
  }

  /**
   * Connect to Gemini Multimodal Live API via WebSocket
   */
  public async connect(): Promise<void> {
    const model = this.options.model || 'gemini-2.0-flash';
    const voiceName = this.options.voiceName || DEFAULT_VOICE;
    const url = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${this.options.apiKey}`;

    console.log(`[GeminiLiveClient] Connecting to Gemini Live API (${model})...`);

    this.ws = new WebSocket(url);

    this.ws.on('open', () => {
      console.log('[GeminiLiveClient] WebSocket Connected! Sending setup configuration...');
      this.isConnected = true;
      this.sendSetup(model, voiceName);
    });

    this.ws.on('message', async (data: WebSocket.Data) => {
      try {
        const messageStr = data.toString();
        const response = JSON.parse(messageStr);
        await this.handleServerResponse(response);
      } catch (err: any) {
        console.error('[GeminiLiveClient] Error handling message:', err);
      }
    });

    this.ws.on('error', (err: Error) => {
      console.error('[GeminiLiveClient] WebSocket Error:', err);
      this.options.onError(err);
    });

    this.ws.on('close', (code: number, reason: string) => {
      console.log(`[GeminiLiveClient] WebSocket Closed (${code}: ${reason})`);
      this.isConnected = false;
      this.options.onClose();
    });
  }

  /**
   * Send initial session setup message to Gemini
   */
  private sendSetup(model: string, voiceName: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const functionDeclarations = this.options.mcpManager.getGeminiFunctionDeclarations();
    
    // Ensure model name format (models/gemini-2.0-flash-exp)
    const modelPath = model.startsWith('models/') ? model : `models/${model}`;

    const setupPayload = {
      setup: {
        model: modelPath,
        generationConfig: {
          responseModalities: ['AUDIO', 'TEXT'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: voiceName
              }
            }
          }
        },
        systemInstruction: {
          parts: [{ text: SYSTEM_PROMPT }]
        },
        tools: [
          {
            functionDeclarations: functionDeclarations
          }
        ]
      }
    };

    console.log(`[GeminiLiveClient] Setup sent for ${modelPath} with ${functionDeclarations.length} tools registered.`);
    this.ws.send(JSON.stringify(setupPayload));
  }

  /**
   * Stream incoming raw PCM audio chunk (16kHz) from microphone to Gemini Live API
   */
  public sendAudioChunk(pcmBuffer: Buffer | Uint8Array): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const base64Pcm = AudioUtils.pcmToBase64(pcmBuffer);
    const audioPayload = AudioUtils.formatRealtimeAudioChunk(base64Pcm);
    this.ws.send(JSON.stringify(audioPayload));
  }

  /**
   * Send text prompt or user message directly to Gemini Live API
   */
  public sendTextMessage(text: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const textPayload = {
      clientContent: {
        turns: [
          {
            role: 'user',
            parts: [{ text }]
          }
        ],
        turnComplete: true
      }
    };

    this.ws.send(JSON.stringify(textPayload));
  }

  /**
   * Process responses received from Gemini Live API
   */
  private async handleServerResponse(response: any): Promise<void> {
    // 1. Process Voice / Text Model Turn
    if (response.serverContent?.modelTurn?.parts) {
      for (const part of response.serverContent.modelTurn.parts) {
        // Audio output (PCM 24kHz Base64)
        if (part.inlineData?.mimeType?.startsWith('audio/pcm')) {
          const pcmBuffer = AudioUtils.base64ToPcm(part.inlineData.data);
          this.options.onAudioData(pcmBuffer);
        }
        // Text transcript output
        if (part.text) {
          this.options.onTextData(part.text);
        }
      }
    }

    // 2. Process Gemini Function Call Request (Tool Call)
    if (response.toolCall?.functionCalls) {
      for (const call of response.toolCall.functionCalls) {
        console.log(`[GeminiLiveClient] Gemini requested Function Call '${call.name}' (ID: ${call.id})`);
        
        // Execute tool call via MCP Manager
        const toolResult = await this.options.mcpManager.executeToolCall(call.name, call.args || {});

        // Send function response back to Gemini
        const toolResponsePayload = AudioUtils.formatToolResponse(call.id, toolResult);
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          console.log(`[GeminiLiveClient] Sending Function Response for ID: ${call.id}`);
          this.ws.send(JSON.stringify(toolResponsePayload));
        }
      }
    }
  }

  /**
   * Close Gemini Live API connection
   */
  public close(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}
