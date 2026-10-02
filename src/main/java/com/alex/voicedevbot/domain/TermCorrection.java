package com.alex.voicedevbot.domain;

/**
 * Whisper adashgan so'z va aslida aytilgani.
 *
 * @param heard transkriptdagi ko'rinishi
 * @param correct nima nazarda tutilgan
 */
public record TermCorrection(String heard, String correct) {

  public TermCorrection {
    if (heard == null || heard.isBlank() || correct == null || correct.isBlank()) {
      throw new IllegalArgumentException("Correction must have both words");
    }
    heard = heard.strip();
    correct = correct.strip();
  }
}
