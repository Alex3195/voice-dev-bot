package com.alex.voicedevbot.domain;

import java.util.Objects;

/**
 * Bitta Claude chaqiruvi qancha token ishlatdi — har task narxini ko'rish va keshni tekshirish
 * uchun.
 *
 * @param model javob bergan model (fallback bo'lsa — so'ralganidan boshqa)
 */
public record LlmUsage(
    ModelId model,
    long inputTokens,
    long cacheReadTokens,
    long cacheWriteTokens,
    long outputTokens) {

  public LlmUsage {
    Objects.requireNonNull(model, "model");
    if (inputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0 || outputTokens < 0) {
      throw new IllegalArgumentException("Token counts must not be negative");
    }
  }
}
