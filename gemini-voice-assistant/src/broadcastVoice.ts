import { ElevenLabsService, FEMALE_VOICES } from './elevenLabsService.js';
import dotenv from 'dotenv';

dotenv.config();

const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY || 'sk_53259a25a6218b4fbd842f504cc35100e51a7a9a3f95876a';
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || process.env.BOT_TOKEN || '';

const SPONTANEOUS_VOICE_SCRIPTS = [
  "صباح الورد والنشاط! أنا زادا.. حبيت أفكّرك تراجع مصاريف الأسبوع وتشوف الرصيد المتاح عشان نظبط خروجة الويكند من غير أي ضغط مالي! يومك سعيد وجميل يا رب 🌸",
  "مساء الخير يا غالي! زادا معاك.. شفت شوية عروض حلوة على البقالة والتموين النهاردة، شيك على مخزون البيت عندك لو في حاجة ناقصة عشان نلحق التوفير بدري بدري 🛒✨",
  "طمني عليك! زادا بتسأل.. كل الفواتير والمصاريف متسجلة تمام؟ لو صرفت أي حاجة في مشوارك ابعتهالي هنا علطول وأنا هظبّطهالك في ثانية 💫",
  "جمعة مباركة ويوم رايق عليك! زادا بتفكرك.. صحتك المالية ممتازة وميزانيتك ماشية زي الفل، استمتع بيومك مع العيلة ولا تشيل هم أي حسابات ☕❤️"
];

export async function broadcastVoiceToUsers(chatIds: (string | number)[], customText?: string): Promise<{ success: number; failed: number }> {
  const elevenLabs = new ElevenLabsService(ELEVENLABS_API_KEY);
  const text = customText || SPONTANEOUS_VOICE_SCRIPTS[Math.floor(Math.random() * SPONTANEOUS_VOICE_SCRIPTS.length)];

  console.log(`[BroadcastVoice] 🎙️ Synthesizing voice note: "${text.slice(0, 40)}..."`);
  const audioBuffer = await elevenLabs.generateSpeech(text, FEMALE_VOICES.ZADA_AI);

  if (!audioBuffer) {
    console.error('[BroadcastVoice] Failed to generate audio buffer');
    return { success: 0, failed: chatIds.length };
  }

  let successCount = 0;
  let failedCount = 0;

  for (const chatId of chatIds) {
    try {
      const blob = new Blob([new Uint8Array(audioBuffer)], { type: 'audio/mpeg' });
      const formData = new FormData();
      formData.append('chat_id', String(chatId));
      formData.append('voice', blob, 'zada_voice_note.mp3');
      formData.append('caption', `🎙️ رسالة صوتية من زادا:\n"${text}"`);

      const res = await fetch(`https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendVoice`, {
        method: 'POST',
        body: formData
      });

      const json = await res.json();
      if (json.ok) {
        console.log(`[BroadcastVoice] ✅ Voice note sent to chat ${chatId}`);
        successCount++;
      } else {
        console.warn(`[BroadcastVoice] ⚠️ Telegram failed for ${chatId}:`, json.description);
        failedCount++;
      }
    } catch (err: any) {
      console.error(`[BroadcastVoice] Error sending to ${chatId}:`, err.message);
      failedCount++;
    }
  }

  return { success: successCount, failed: failedCount };
}
