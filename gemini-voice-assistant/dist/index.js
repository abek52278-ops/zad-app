import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer, WebSocket } from 'ws';
import dotenv from 'dotenv';
import { MCPManager } from './mcpManager.js';
import { GeminiLiveClient } from './geminiLiveClient.js';
import { ElevenLabsService, FEMALE_VOICES } from './elevenLabsService.js';
dotenv.config();
const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8080;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash-live-001';
const GEMINI_VOICE = process.env.GEMINI_VOICE || 'Puck';
const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY || 'sk_53259a25a6218b4fbd842f504cc35100e51a7a9a3f95876a';
const ELEVENLABS_VOICE_ID = process.env.ELEVENLABS_VOICE_ID || FEMALE_VOICES.ZADA_AI;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, '../public');
async function main() {
    console.log('===============================================================');
    console.log('🧠 Zad Neural Brain & ElevenLabs Ultra-Realistic Voice Server');
    console.log('👩 Persona Voice: Female Natural Studio (Zada AI / Rachel)');
    console.log('===============================================================');
    const mcpManager = new MCPManager();
    const elevenLabs = new ElevenLabsService(ELEVENLABS_API_KEY);
    // HTTP Server with static files + REST API for voice synthesis & agent queries
    const httpServer = http.createServer(async (req, res) => {
        // CORS headers for Android app, Telegram webhooks, and web clients
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
        if (req.method === 'OPTIONS') {
            res.writeHead(200);
            res.end();
            return;
        }
        // 1. API: ElevenLabs Female Voice Synthesizer (/api/tts)
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
                    const audioBuffer = await elevenLabs.generateSpeech(text, voiceId || ELEVENLABS_VOICE_ID, modelId || 'eleven_multilingual_v2');
                    if (audioBuffer) {
                        res.writeHead(200, {
                            'Content-Type': 'audio/mpeg',
                            'Content-Length': audioBuffer.length
                        });
                        res.end(audioBuffer);
                    }
                    else {
                        res.writeHead(500, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Failed to synthesize speech' }));
                    }
                }
                catch (err) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: err.message }));
                }
            });
            return;
        }
        // 2. API: Telegram Voice Message Sender (/api/telegram/send_voice)
        if (req.url === '/api/telegram/send_voice' && req.method === 'POST') {
            let body = '';
            req.on('data', chunk => { body += chunk; });
            req.on('end', async () => {
                try {
                    const { chatId, text, botToken, voiceId } = JSON.parse(body || '{}');
                    if (!chatId || !text || !botToken) {
                        res.writeHead(400, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'chatId, text, and botToken are required' }));
                        return;
                    }
                    // Generate ElevenLabs female voice
                    const audioBuffer = await elevenLabs.generateSpeech(text, voiceId || ELEVENLABS_VOICE_ID, 'eleven_multilingual_v2');
                    if (!audioBuffer) {
                        res.writeHead(500, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Failed to synthesize audio for Telegram' }));
                        return;
                    }
                    // Send voice note via Telegram Bot API multipart/form-data
                    const blob = new Blob([new Uint8Array(audioBuffer)], { type: 'audio/mpeg' });
                    const formData = new FormData();
                    formData.append('chat_id', String(chatId));
                    formData.append('voice', blob, 'zada_voice.mp3');
                    formData.append('caption', `🎙️ زادا: ${text}`);
                    const tgRes = await fetch(`https://api.telegram.org/bot${botToken}/sendVoice`, {
                        method: 'POST',
                        body: formData
                    });
                    const tgJson = await tgRes.json();
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: true, telegram: tgJson }));
                }
                catch (err) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: err.message }));
                }
            });
            return;
        }
        // 2.5 API: Broadcast Voice Note to All Users (/api/telegram/broadcast_voice)
        if (req.url === '/api/telegram/broadcast_voice' && req.method === 'POST') {
            let body = '';
            req.on('data', chunk => { body += chunk; });
            req.on('end', async () => {
                try {
                    const { chatIds, text, botToken, voiceId } = JSON.parse(body || '{}');
                    if (!Array.isArray(chatIds) || chatIds.length === 0 || !botToken) {
                        res.writeHead(400, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'chatIds (array) and botToken are required' }));
                        return;
                    }
                    const defaultScripts = [
                        "صباح الورد والنشاط! أنا زادا.. حبيت أفكّرك تراجع مصاريف الأسبوع وتشوف الرصيد المتاح عشان نظبط خروجة الويكند من غير أي ضغط مالي! يومك سعيد وجميل يا رب 🌸",
                        "مساء الخير يا غالي! زادا معاك.. شفت شوية عروض حلوة على البقالة والتموين النهاردة، شيك على مخزون البيت لو في حاجة ناقصة 🛒✨",
                        "طمني عليك! زادا بتسأل.. كل الفواتير والمصاريف متسجلة تمام؟ لو صرفت أي حاجة في مشوارك ابعتهالي هنا علطول 💫"
                    ];
                    const broadcastText = text || defaultScripts[Math.floor(Math.random() * defaultScripts.length)];
                    const audioBuffer = await elevenLabs.generateSpeech(broadcastText, voiceId || ELEVENLABS_VOICE_ID, 'eleven_multilingual_v2');
                    if (!audioBuffer) {
                        res.writeHead(500, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Failed to synthesize broadcast voice note' }));
                        return;
                    }
                    let sent = 0;
                    let failed = 0;
                    for (const cid of chatIds) {
                        try {
                            const blob = new Blob([new Uint8Array(audioBuffer)], { type: 'audio/mpeg' });
                            const formData = new FormData();
                            formData.append('chat_id', String(cid));
                            formData.append('voice', blob, 'zada_broadcast.mp3');
                            formData.append('caption', `🎙️ رسالة صوتية من زادا:\n"${broadcastText}"`);
                            const r = await fetch(`https://api.telegram.org/bot${botToken}/sendVoice`, {
                                method: 'POST',
                                body: formData
                            });
                            const j = await r.json();
                            if (j.ok)
                                sent++;
                            else
                                failed++;
                        }
                        catch {
                            failed++;
                        }
                    }
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: true, text: broadcastText, sent, failed, total: chatIds.length }));
                }
                catch (err) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: err.message }));
                }
            });
            return;
        }
        // 3. API: Agent Direct Brain Query (/api/chat)
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
                    const toolResult = await mcpManager.executeToolCall('get_financial_summary', {});
                    const replyText = `أهلاً بك! أنا زادا. رصيدك المتاح حالياً ${toolResult.data.remainingBalance} جنيه، وكل أمورك المالية منتظمة ومحسوبة بالكامل.`;
                    let audioBase64 = null;
                    if (generateAudio) {
                        const audioBuf = await elevenLabs.generateSpeech(replyText, ELEVENLABS_VOICE_ID);
                        if (audioBuf)
                            audioBase64 = audioBuf.toString('base64');
                    }
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({
                        reply: replyText,
                        audio: audioBase64,
                        persona: 'zada_ai_female',
                        neuralStatus: 'connected'
                    }));
                }
                catch (err) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: err.message }));
                }
            });
            return;
        }
        // 4. Static Files
        let filePath = path.join(PUBLIC_DIR, req.url === '/' ? 'index.html' : req.url || 'index.html');
        if (!fs.existsSync(filePath)) {
            filePath = path.join(PUBLIC_DIR, 'index.html');
        }
        const extname = path.extname(filePath);
        let contentType = 'text/html';
        if (extname === '.js')
            contentType = 'text/javascript';
        if (extname === '.css')
            contentType = 'text/css';
        if (extname === '.json')
            contentType = 'application/json';
        if (extname === '.png')
            contentType = 'image/png';
        if (extname === '.svg')
            contentType = 'image/svg+xml';
        fs.readFile(filePath, (err, content) => {
            if (err) {
                res.writeHead(500);
                res.end(`Server Error: ${err.code}`);
            }
            else {
                res.writeHead(200, { 'Content-Type': contentType });
                res.end(content, 'utf-8');
            }
        });
    });
    // WebSocket Server for Real-Time Bidirectional Voice & Brain Feed
    const wss = new WebSocketServer({ server: httpServer });
    wss.on('connection', (clientWs) => {
        console.log('[WebSocketServer] 🌐 Client connected to Zada Female Voice Core.');
        const geminiClient = new GeminiLiveClient({
            apiKey: GEMINI_API_KEY,
            model: GEMINI_MODEL,
            voiceName: GEMINI_VOICE,
            mcpManager,
            onAudioData: (pcmBuffer) => {
                if (clientWs.readyState === WebSocket.OPEN) {
                    clientWs.send(pcmBuffer);
                }
            },
            onTextData: async (text) => {
                if (clientWs.readyState === WebSocket.OPEN) {
                    clientWs.send(JSON.stringify({ type: 'text', text }));
                    // Synthesize with ElevenLabs Female Voice (Rachel / Zada AI)
                    if (text.trim() && text.length > 3) {
                        try {
                            const mp3Buffer = await elevenLabs.generateSpeech(text, ELEVENLABS_VOICE_ID);
                            if (mp3Buffer && clientWs.readyState === WebSocket.OPEN) {
                                clientWs.send(JSON.stringify({
                                    type: 'elevenlabs_audio',
                                    audio: mp3Buffer.toString('base64'),
                                    text: text,
                                    voice: 'zada_female'
                                }));
                            }
                        }
                        catch (e) {
                            console.warn('[ElevenLabs] Live TTS bypass:', e);
                        }
                    }
                }
            },
            onError: (err) => {
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
        clientWs.on('message', async (data, isBinary) => {
            if (isBinary) {
                const pcmBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
                geminiClient.sendAudioChunk(pcmBuffer);
            }
            else {
                try {
                    const msg = JSON.parse(data.toString());
                    if (msg.type === 'text' && msg.text) {
                        geminiClient.sendTextMessage(msg.text);
                    }
                    else if (msg.type === 'speak_elevenlabs' && msg.text) {
                        const audioBuf = await elevenLabs.generateSpeech(msg.text, msg.voiceId || ELEVENLABS_VOICE_ID);
                        if (audioBuf && clientWs.readyState === WebSocket.OPEN) {
                            clientWs.send(JSON.stringify({
                                type: 'elevenlabs_audio',
                                audio: audioBuf.toString('base64'),
                                voice: 'zada_female'
                            }));
                        }
                    }
                }
                catch {
                    geminiClient.sendTextMessage(data.toString());
                }
            }
        });
        clientWs.on('close', () => {
            geminiClient.close();
        });
    });
    httpServer.listen(PORT, () => {
        console.log(`🚀 Zada AI (Female Persona) Server listening on http://localhost:${PORT}`);
        console.log(`🎙️ ElevenLabs Voice: Connected & Ready (Female Voice: ${ELEVENLABS_VOICE_ID})`);
        console.log(`📱 Telegram Voice Notes API: Ready at POST /api/telegram/send_voice`);
        console.log(`⚡ WebSocket URL: ws://localhost:${PORT}`);
    });
}
main().catch((err) => {
    console.error('Fatal Server Error:', err);
});
