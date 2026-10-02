package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioRef;

/** Havola bo'yicha audio faylni olib keladi. */
public interface AudioSource {

  /**
   * @throws AudioUnavailableException audio olib bo'lmasa
   */
  AudioClip fetch(AudioRef ref);
}
