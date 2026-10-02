package com.alex.voicedevbot.domain;

import java.util.Set;

/** Botdan foydalanishga ruxsat berilgan foydalanuvchilar ro'yxati (whitelist). */
public record AccessPolicy(Set<TelegramUserId> allowedUsers) {

  public AccessPolicy {
    if (allowedUsers == null || allowedUsers.isEmpty()) {
      throw new IllegalArgumentException("At least one allowed user is required");
    }
    allowedUsers = Set.copyOf(allowedUsers);
  }

  public boolean isAllowed(TelegramUserId user) {
    return allowedUsers.contains(user);
  }
}
