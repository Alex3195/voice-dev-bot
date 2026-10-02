package com.alex.voicedevbot.adapter.out.stt;

import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.Transcript;

/** Whisper ulanmaguncha ishlatiladigan vaqtinchalik STT: faqat audio hajmini qaytaradi. */
public class StubSpeechToText implements SpeechToText {

  static final String STUB_TEXT = "[stub] %d bayt audio qabul qilindi (%s). STT hali ulanmagan.";

  @Override
  public Transcript transcribe(AudioClip audio) {
    return new Transcript(STUB_TEXT.formatted(audio.size(), audio.mimeType()));
  }
}
