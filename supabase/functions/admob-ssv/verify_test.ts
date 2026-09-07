import { assertEquals } from "jsr:@std/assert@1";
import {
  b64urlToBytes,
  derToRawSignature,
  isFreshTimestamp,
  pickKey,
  signedContent,
} from "./verify.ts";

/**
 * التحقق من توقيع AdMob SSV.
 *
 * الفرق بين ده وبين zad_ad_reward_grant: الـRPC محكومة بالهوية وبفاصل ١٠ ثواني
 * وسقف ٣٠/يوم، بس **مفيهاش إثبات إن الإعلان اتشاف**. مستخدم مسجّل دخول يقدر
 * ينادّيها مباشرة. التوقيع بمفتاح جوجل هو اللي بيميّز نداءها عن أي نداء تاني.
 */

Deno.test("signedContent بياخد كل اللي قبل &signature= بالنص الخام", () => {
  const q = "ad_network=5450213213286189855&ad_unit=123&custom_data=uid-1" +
    "&reward_amount=1&timestamp=1700000000000&transaction_id=T1" +
    "&signature=ABC&key_id=3335741209";
  assertEquals(
    signedContent(q),
    "ad_network=5450213213286189855&ad_unit=123&custom_data=uid-1" +
      "&reward_amount=1&timestamp=1700000000000&transaction_id=T1",
  );
});

/**
 * الترتيب والترميز لازم يفضلوا زي ما وصلوا. إعادة بناء السلسلة من
 * URLSearchParams بتعيد الترميز وبتكسر التحقق — والفشل ده بيبان "توقيع غلط"
 * مش "بنيت السلسلة غلط"، وده بيوجّه التشخيص للمكان الخطأ.
 */
Deno.test("signedContent مابيعيدش ترتيب ولا ترميز", () => {
  const q = "z=1&a=%D8%B2&signature=X";
  assertEquals(signedContent(q), "z=1&a=%D8%B2");
});

Deno.test("signedContent بيرجع null من غير signature", () => {
  assertEquals(signedContent("a=1&b=2"), null);
});

Deno.test("b64urlToBytes بيفك ترميز base64url بحروفه الخاصة", () => {
  // "~~~?" في base64 عادي = fn5-Pw== ، وفي base64url = fn5-Pw (بـ- و_)
  const bytes = b64urlToBytes("fn5-Pw");
  assertEquals(Array.from(bytes), [126, 126, 126, 63]);
});

Deno.test("b64urlToBytes بيتعامل مع الحشو الناقص", () => {
  assertEquals(Array.from(b64urlToBytes("QQ")), [65]);
  assertEquals(Array.from(b64urlToBytes("QUI")), [65, 66]);
});

/**
 * DER→raw هي أكتر خطوة بيتنسى فيها الحشو. r و s ممكن يبقوا أقصر من ٣٢ بايت
 * (لازم يتحشوا من الشمال) أو ٣٣ ببايت صفر في الأول (لازم يتقص).
 */
Deno.test("derToRawSignature بيحشي الأعداد القصيرة من الشمال", () => {
  // SEQUENCE { INTEGER 0x01, INTEGER 0x02 }
  const der = new Uint8Array([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02]);
  const raw = derToRawSignature(der)!;
  assertEquals(raw.length, 64);
  assertEquals(raw[31], 1);
  assertEquals(raw[63], 2);
  assertEquals(raw[0], 0);
});

Deno.test("derToRawSignature بيقص بايت الصفر البادئ", () => {
  const r = new Uint8Array(33); r[0] = 0x00; r[32] = 0x09;
  const der = new Uint8Array([0x30, 0x26, 0x02, 0x21, ...r, 0x02, 0x01, 0x07]);
  const raw = derToRawSignature(der)!;
  assertEquals(raw.length, 64);
  assertEquals(raw[31], 9);
  assertEquals(raw[63], 7);
});

Deno.test("derToRawSignature بيرفض اللي مش DER", () => {
  assertEquals(derToRawSignature(new Uint8Array([1, 2, 3])), null);
  assertEquals(derToRawSignature(new Uint8Array([0x31, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02])), null);
});

Deno.test("pickKey بيطابق بالرقم مش بالنص", () => {
  const keys = [{ keyId: 111, base64: "a" }, { keyId: 222, base64: "b" }];
  assertEquals(pickKey(keys, "222")?.base64, "b");
  assertEquals(pickKey(keys, "999"), null);
  assertEquals(pickKey(keys, null), null);
  assertEquals(pickKey(keys, "abc"), null);
});

/** نافذة زمنية: من غيرها نفس الرابط بيفضل صالح للأبد ويتعاد تشغيله. */
Deno.test("isFreshTimestamp بيرفض القديم والمستقبل البعيد", () => {
  const now = 1_700_000_000_000;
  assertEquals(isFreshTimestamp(String(now), now), true);
  assertEquals(isFreshTimestamp(String(now - 30 * 60_000), now), true);
  assertEquals(isFreshTimestamp(String(now - 2 * 60 * 60_000), now), false);
  assertEquals(isFreshTimestamp(String(now + 10 * 60_000), now), false);
  assertEquals(isFreshTimestamp(null, now), false);
  assertEquals(isFreshTimestamp("not-a-number", now), false);
});
