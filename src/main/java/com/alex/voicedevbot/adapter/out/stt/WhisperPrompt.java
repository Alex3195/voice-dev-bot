package com.alex.voicedevbot.adapter.out.stt;

import com.alex.voicedevbot.domain.SpeechLanguage;
import java.util.Map;

/**
 * Whisper'ning boshlang'ich prompt'i — faqat til namunasi (usiz matn kirill/turkcha imloga o'tib
 * ketadi).
 *
 * <p>Project lug'ati bu yerga qo'shilmaydi: qisqa yoki notanish talaffuzdagi audioda Whisper
 * prompt'ni "oldingi gap" deb davom ettiradi va aytilgan gap o'rniga lug'at so'zlarini takrorlab
 * yozadi. Atamalarni Claude tuzatilgan matnda to'g'rilaydi.
 */
final class WhisperPrompt {

  private WhisperPrompt() {}

  static String build(SpeechLanguage language, Map<String, String> basePrompts) {
    return basePrompts.getOrDefault(language.code(), "").strip();
  }
}
