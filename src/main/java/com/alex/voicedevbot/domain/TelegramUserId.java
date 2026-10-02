package com.alex.voicedevbot.domain;

/** Telegram foydalanuvchisining raqamli identifikatori. */
public record TelegramUserId(long value) {

  public TelegramUserId {
    if (value <= 0) {
      throw new IllegalArgumentException("Telegram user id must be positive: " + value);
    }
  }
}
