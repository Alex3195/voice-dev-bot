package com.alex.voicedevbot.application.port.out;

/** STT dvigateli audioni matnga aylantira olmadi. */
public class TranscriptionException extends RuntimeException {

  public TranscriptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
