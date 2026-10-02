package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.Transcript;

/** Nutqni matnga aylantiruvchi dvigatel (lokal Whisper, tashqi API va h.k.). */
public interface SpeechToText {

  /**
   * @throws TranscriptionException audio matnga aylantirilmasa
   */
  Transcript transcribe(AudioClip audio);
}
