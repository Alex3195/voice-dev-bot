package com.alex.voicedevbot.application.port.in;

/** Claude bilan ishlab bo'lmadi — foydalanuvchiga tushunarli sabab. */
public enum LanguageModelProblem {
  /** Kalit berilmagan: Claude o'chiq, oddiy qoralama ishlatiladi. */
  NOT_CONFIGURED,
  UNAUTHORIZED,
  MODEL_UNAVAILABLE,
  REFUSED,
  INVALID_RESPONSE,
  UNAVAILABLE
}
