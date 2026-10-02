package com.alex.voicedevbot.domain;

/** Hali yuklab olinmagan audio faylga havola (masalan, Telegram file_id). */
public record AudioRef(String id, String mimeType) {

  public AudioRef {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Audio id must not be blank");
    }
    if (mimeType == null || mimeType.isBlank()) {
      throw new IllegalArgumentException("Audio mime type must not be blank");
    }
  }
}
