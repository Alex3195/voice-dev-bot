package com.alex.voicedevbot.application.port.out;

import java.util.Objects;

/** Claude so'rovi bajarilmadi. Xabarda kalit va transkript bo'lmaydi. */
public class LanguageModelException extends RuntimeException {

  public enum Reason {
    /** {@code ANTHROPIC_API_KEY} berilmagan — Claude ishlatilmaydi. */
    NOT_CONFIGURED,
    /** Kalit noto'g'ri yoki ruxsat yo'q (HTTP 401/403). */
    UNAUTHORIZED,
    /** Tanlangan model yo'q yoki bu so'rovni qo'llamaydi. */
    MODEL_UNAVAILABLE,
    /** Xavfsizlik filtri so'rovni rad etdi. */
    REFUSED,
    /** Javob sxemaga mos emas yoki chala. */
    INVALID_RESPONSE,
    /** Tarmoq, limit yoki server xatosi. */
    UNAVAILABLE
  }

  private final Reason reason;

  public LanguageModelException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public Reason reason() {
    return reason;
  }
}
