package com.alex.voicedevbot.application.port.out;

/** Audio faylni olib bo'lmadi. */
public class AudioUnavailableException extends RuntimeException {

  public AudioUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
