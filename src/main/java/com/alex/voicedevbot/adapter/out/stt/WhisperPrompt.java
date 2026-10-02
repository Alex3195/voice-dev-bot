package com.alex.voicedevbot.adapter.out.stt;

import com.alex.voicedevbot.domain.TranscriptionHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Whisper'ning boshlang'ich prompt'i: til namunasi + project atamalari.
 *
 * <p>Whisper prompt'ning faqat oxirgi ~224 tokenini ishlatadi; o'zbekcha matnda bu taxminan 600
 * belgi. Sig'maydigan atamalar tashlab yuboriladi (lug'at boshidagilar ustun).
 */
final class WhisperPrompt {

  static final int MAX_LENGTH = 600;

  private WhisperPrompt() {}

  static String build(TranscriptionHints hints, Map<String, String> basePrompts) {
    String base = basePrompts.getOrDefault(hints.language().code(), "").strip();
    List<String> fitting = new ArrayList<>();
    int length = base.length() + 1;
    for (String term : hints.vocabulary()) {
      length += term.length() + 2;
      if (length > MAX_LENGTH) {
        break;
      }
      fitting.add(term);
    }
    if (fitting.isEmpty()) {
      return base;
    }
    String terms = String.join(", ", fitting) + ".";
    return base.isEmpty() ? terms : base + " " + terms;
  }
}
