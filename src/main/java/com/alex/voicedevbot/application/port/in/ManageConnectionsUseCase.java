package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * GitLab ulanishlari: gitlab.com, self-hosted yoki boshqa istalgan GitLab. Token saqlashdan oldin
 * GitLab'da tekshiriladi.
 */
public interface ManageConnectionsUseCase {

  ConnectionResult list(TelegramUserId user);

  ConnectionResult show(TelegramUserId user, long connectionId);

  /**
   * @param address foydalanuvchi yozgan manzil ({@code gitlab.com}, {@code https://git.example.uz})
   * @param token foydalanuvchi yozgan token
   */
  ConnectionResult add(TelegramUserId user, String address, String token);

  /** Yangi token o'sha GitLab foydalanuvchisiniki bo'lishi kerak. */
  ConnectionResult renew(TelegramUserId user, long connectionId, String token);

  ConnectionResult remove(TelegramUserId user, long connectionId);
}
