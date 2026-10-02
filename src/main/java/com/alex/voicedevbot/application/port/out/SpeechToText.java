package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptionHints;

/** Nutqni matnga aylantiruvchi dvigatel (lokal Whisper, tashqi API va h.k.). */
public interface SpeechToText {

  /**
   * @param hints nutq tili va kutilayotgan atamalar — dvigatel ularni o'zicha ishlatadi
   * @throws TranscriptionException audio matnga aylantirilmasa
   */
  Transcript transcribe(AudioClip audio, TranscriptionHints hints);
}
