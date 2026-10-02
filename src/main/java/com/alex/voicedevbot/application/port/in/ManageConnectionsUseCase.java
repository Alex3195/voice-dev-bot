package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * Xizmatlarga ulanishlar: GitLab (gitlab.com yoki self-hosted), GitHub. Token saqlashdan oldin
 * xizmatning o'zida tekshiriladi va muddati tugaguncha shu serverdagi barcha projectlar uchun
 * ishlatiladi.
 */
public interface ManageConnectionsUseCase {

  ConnectionResult list(TelegramUserId user);

  ConnectionResult show(TelegramUserId user, long connectionId);

  /**
   * @param address foydalanuvchi yozgan manzil ({@code https://git.example.uz}); bo'sh bo'lsa —
   *     xizmatning standart serveri ({@code gitlab.com}, {@code github.com})
   * @param token foydalanuvchi yozgan token
   */
  ConnectionResult add(TelegramUserId user, Provider provider, String address, String token);

  /** Yangi token o'sha xizmat foydalanuvchisiniki bo'lishi kerak. */
  ConnectionResult renew(TelegramUserId user, long connectionId, String token);

  ConnectionResult remove(TelegramUserId user, long connectionId);
}
