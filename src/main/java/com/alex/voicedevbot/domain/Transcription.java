package com.alex.voicedevbot.domain;

import java.util.Objects;

/**
 * STT dvigatelining natijasi va u qanday olingani — sozlamalarni keyin o'lchash uchun.
 *
 * @param prompt dvigatelga yuborilgan boshlang'ich prompt; yuborilmagan bo'lsa bo'sh
 * @param model dvigatel va model nomi (masalan, {@code whisper.cpp ggml-large-v3-q5_0})
 */
public record Transcription(Transcript transcript, String prompt, String model) {

  public Transcription {
    Objects.requireNonNull(transcript, "transcript");
    Objects.requireNonNull(prompt, "prompt");
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Model must not be blank");
    }
  }
}
