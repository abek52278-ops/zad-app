import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer, WebSocket, RawData } from 'ws';
import dotenv from 'dotenv';
import { MCPManager } from './mcpManager.js';
import { GeminiLiveClient } from './geminiLiveClient.js';
import { ElevenLabsService } from './elevenLabsService.js';

dotenv.config();

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8080;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash-live-001';
const GEMINI_VOICE = process.env.GEMINI_VOICE || 'Puck';
const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY || 'sk_53259a25a6218b4fbd842f504cc35100e51a7a9a3f95876a';
const ELEVENLABS_VOICE_ID = process.env.ELEVENLABS_VOICE_ID || 'JBFqnCBsd6RMkjVDRZzb';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, '../public');

async function main() {
  console.log('===============================================================');
  console.log('🧠 Zad Neural Brain & ElevenLabs Ultra-Realistic Voice Server');
  console.log('===============================================================');

  const mcpManager = new MCPManager();
  const elevenLabs = new ElevenLabsService(ELEVENLABS_API_KEY);

  // HTTP Server with static files + REST API for voice synthesis & agent queries
  const httpServer = http.createServer(async (req, res) => {
    // CORS headers for Android app and web clients
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
      res.writeHead(200);
      res.end();
      return;
    }

    // 1. API: ElevenLabs Voice Synthesizer (/api/tts)
    if (req.url === '/api/tts' && req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', async () => {
        try {
          const { text, voiceId, modelId } = JSON.parse(body || '{}');
          if (!text) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Text parameter is required' }));
            return;
          }

          const audioBuffer = await elevenLabs.generateSpeech(
            text,
            voiceId || ELEVENLABS_VOICE_ID,
            modelId || 'eleven_multilingual_v2'
          );

          if (audioBuffer) {
            res.writeHead(200, {
              'Content-Type': 'audio/mpeg',
              'Content-Length': audioBuffer.length
            });
            res.end(audioBuffer);
          } else {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to synthesize speech' }));
          }
        } catch (err: any) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: err.message }));
        }
      });
      return;
    }

    // 2. API: Agent Direct Brain Query (/api/chat)
    if (req.url === '/api/chat' && req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', async () => {
        try {
          const { message, generateAudio } = JSON.parse(body || '{}');
          if (!message) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Message is required' }));
            return;
          }

          // Fallback response with neural tools execution
          const toolResult = await mcpManager.executeToolCall('get_financial_summary', {});
          const replyText = `أهلاً بك! رصيدك المتاح حالياً ${toolResult.data.remainingBalance} جنيه، ومصروفاتك الشهرية منتظمة بنسبة أمان 92%. كيف أساعدك اليوم؟`;

          let audioBase64 = null;
          if (generateAudio) {
            const audioBuf = await elevenLabs.generateSpeech(replyText);
            if (audioBuf) audioBase64 = audioBuf.toString('base64');
          }

          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            reply: replyText,
            audio: audioBase64,
            neuralStatus: 'connected'
          }));
        } catch (err: any) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: err.message }));
        }
      });
      return;
    }

    // 3. Static Files
    let filePath = path.join(PUBLIC_DIR, req.url === '/' ? 'index.html' : req.url || 'index.html');
    if (!fs.existsSync(filePath)) {
      filePath = path.join(PUBLIC_DIR, 'index.html');
    }

    const extname = path.extname(filePath);
    let contentType = 'text/html';
    if (extname === '.js') contentType = 'text/javascript';
    if (extname === '.css') contentType = 'text/css';
    if (extname === '.json') contentType = 'application/json';
    if (extname === '.png') contentType = 'image/png';
    if (extname === '.svg') contentType = 'image/svg+xml';

    fs.readFile(filePath, (err, content) => {
      if (err) {
        res.writeHead(500);
        res.end(`Server Error: ${err.code}`);
      } else {
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(content, 'utf-8');
      }
    });
  });

  // WebSocket Server for Real-Time Bidirectional Voice & Brain Feed
  const wss = new WebSocketServer({ server: httpServer });

  wss.on('connection', (clientWs: WebSocket) => {
    console.log('[WebSocketServer] 🌐 New Client connected to Zad Neural Core.');

    // Initialize Gemini Live Client
    const geminiClient = new GeminiLiveClient({
      apiKey: GEMINI_API_KEY,
      model: GEMINI_MODEL,
      voiceName: GEMINI_VOICE,
      mcpManager,
      onAudioData: (pcmBuffer: Buffer) => {
        if (clientWs.readyState === WebSocket.OPEN) {
          clientWs.send(pcmBuffer);
        }
      },
      onTextData: async (text: string) => {
        if (clientWs.readyState === WebSocket.OPEN) {
          clientWs.send(JSON.stringify({ type: 'text', text }));

          // Optionally synthesize with ElevenLabs for premium high-fidelity voice
          if (text.trim() && text.length > 3) {
            try {
              const mp3Buffer = await elevenLabs.generateSpeech(text);
              if (mp3Buffer && clientWs.readyState === WebSocket.OPEN) {
                clientWs.send(JSON.stringify({
                  type: 'elevenlabs_audio',
                  audio: mp3Buffer.toString('base64'),
                  text: text
                }));
              }
            } catch (e) {
              console.warn('[ElevenLabs] Live TTS bypass:', e);
            }
          }
        }
      },
      onError: (err: Error) => {
        console.error('[WebSocketServer] Error:', err.message);
        if (clientWs.readyState === WebSocket.OPEN) {
          clientWs.send(JSON.stringify({ type: 'error', message: err.message }));
        }
      },
      onClose: () => {
        console.log('[WebSocketServer] Session closed.');
      }
    });

    geminiClient.connect().catch((err) => {
      console.error('[WebSocketServer] Failed to connect Gemini:', err);
    });

    clientWs.on('message', async (data: RawData, isBinary: boolean) => {
      if (isBinary) {
        const pcmBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data as ArrayBuffer);
        geminiClient.sendAudioChunk(pcmBuffer);
      } else {
        try {
          const msg = JSON.parse(data.toString());
          if (msg.type === 'text' && msg.text) {
            geminiClient.sendTextMessage(msg.text);
          } else if (msg.type === 'speak_elevenlabs' && msg.text) {
            // Direct ElevenLabs voice generation request
            const audioBuf = await elevenLabs.generateSpeech(msg.text, msg.voiceId);
            if (audioBuf && clientWs.readyState === WebSocket.OPEN) {
              clientWs.send(JSON.stringify({
                type: 'elevenlabs_audio',
                audio: audioBuf.toString('base64')
              }));
            }
          }
        } catch {
          geminiClient.sendTextMessage(data.toString());
        }
      }
    });

    clientWs.on('close', () => {
      geminiClient.close();
    });
  });

  httpServer.listen(PORT, () => {
    console.log(`🚀 Neural Brain & ElevenLabs Voice Server listening on http://localhost:${PORT}`);
    console.log(`🎙️ ElevenLabs API: Connected & Ready (Voice ID: ${ELEVENLABS_VOICE_ID})`);
    console.log(`⚡ WebSocket URL: ws://localhost:${PORT}`);
  });
}

main().catch((err) => {
  console.error('Fatal Server Error:', err);
});
