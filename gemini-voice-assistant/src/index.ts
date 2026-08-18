import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer, WebSocket, RawData } from 'ws';
import dotenv from 'dotenv';
import { MCPManager } from './mcpManager.js';
import { GeminiLiveClient } from './geminiLiveClient.js';

dotenv.config();

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8080;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
const GEMINI_VOICE = process.env.GEMINI_VOICE || 'Puck';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, '../public');

async function main() {
  console.log('---------------------------------------------------------');
  console.log('🎙️ Starting Genesis Arabic Real-time Voice Assistant Server');
  console.log('---------------------------------------------------------');

  if (!GEMINI_API_KEY) {
    console.warn('⚠️ WARNING: GEMINI_API_KEY is not set in environment variables.');
    console.warn('Please set GEMINI_API_KEY in your .env file or environment.');
  }

  // 1. Initialize MCP Manager
  const mcpManager = new MCPManager();

  // Optionally connect stdio MCP servers if configured in env
  if (process.env.SQLITE_DB_PATH) {
    await mcpManager.connectStdioServer('sqlite', 'npx', ['-y', '@modelcontextprotocol/server-sqlite', process.env.SQLITE_DB_PATH]);
  }

  // 2. Create HTTP Server to serve static HTML test interface
  const httpServer = http.createServer((req, res) => {
    let filePath = path.join(PUBLIC_DIR, req.url === '/' ? 'index.html' : req.url || 'index.html');
    if (!fs.existsSync(filePath)) {
      filePath = path.join(PUBLIC_DIR, 'index.html');
    }

    const extname = path.extname(filePath);
    let contentType = 'text/html';
    if (extname === '.js') contentType = 'text/javascript';
    if (extname === '.css') contentType = 'text/css';
    if (extname === '.json') contentType = 'application/json';

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

  // 3. Create WebSocket Server for Browser / Mobile Client Connections
  const wss = new WebSocketServer({ server: httpServer });

  wss.on('connection', (clientWs: WebSocket) => {
    console.log('[WebSocketServer] New client connected to Voice Server.');

    // Initialize dedicated Gemini Multimodal Live API Client for this user session
    const geminiClient = new GeminiLiveClient({
      apiKey: GEMINI_API_KEY,
      model: GEMINI_MODEL,
      voiceName: GEMINI_VOICE,
      mcpManager,
      onAudioData: (pcmBuffer: Buffer) => {
        if (clientWs.readyState === WebSocket.OPEN) {
          // Send audio frame as binary message to client
          clientWs.send(pcmBuffer);
        }
      },
      onTextData: (text: string) => {
        if (clientWs.readyState === WebSocket.OPEN) {
          // Send text transcript as JSON message to client
          clientWs.send(JSON.stringify({ type: 'text', text }));
        }
      },
      onError: (err: Error) => {
        console.error('[WebSocketServer] Gemini Client error:', err.message);
        if (clientWs.readyState === WebSocket.OPEN) {
          clientWs.send(JSON.stringify({ type: 'error', message: err.message }));
        }
      },
      onClose: () => {
        console.log('[WebSocketServer] Gemini Live connection closed for client.');
      }
    });

    // Connect to Gemini Live API
    geminiClient.connect().catch((err) => {
      console.error('[WebSocketServer] Failed to connect to Gemini:', err);
    });

    // Handle Client Messages (Audio input or JSON text command)
    clientWs.on('message', (data: RawData, isBinary: boolean) => {
      if (isBinary) {
        // Raw PCM 16kHz microphone audio from client
        const pcmBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data as ArrayBuffer);
        geminiClient.sendAudioChunk(pcmBuffer);
      } else {
        try {
          const message = JSON.parse(data.toString());
          if (message.type === 'text' && message.text) {
            geminiClient.sendTextMessage(message.text);
          }
        } catch {
          // Plain text message
          geminiClient.sendTextMessage(data.toString());
        }
      }
    });

    clientWs.on('close', () => {
      console.log('[WebSocketServer] Client disconnected.');
      geminiClient.close();
    });
  });

  // 4. Start Server
  httpServer.listen(PORT, () => {
    console.log(`🚀 Voice Assistant Server listening at http://localhost:${PORT}`);
    console.log(`⚡ WebSocket endpoint: ws://localhost:${PORT}`);
    console.log(`🎙️ Gemini Model: ${GEMINI_MODEL} | Voice: ${GEMINI_VOICE}`);
  });
}

main().catch((err) => {
  console.error('Fatal Server Error:', err);
});
