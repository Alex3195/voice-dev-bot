package com.alex.voicedevbot.domain;

/**
 * Repo yaratish mumkin bo'lgan joy: foydalanuvchining shaxsiy namespace'i yoki guruh.
 *
 * @param path masalan {@code alex} yoki {@code akfa/backend}
 */
public record Namespace(long id, String path, boolean personal) {

  public Namespace {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Namespace path must not be blank");
    }
  }
}
