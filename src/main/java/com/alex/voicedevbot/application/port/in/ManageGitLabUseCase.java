package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * GitLab ulanishlari: gitlab.com, self-hosted yoki boshqa istalgan GitLab. Token saqlashdan oldin
 * GitLab'da tekshiriladi.
 */
public interface ManageGitLabUseCase {

  GitLabResult list(TelegramUserId user);

  GitLabResult show(TelegramUserId user, long connectionId);

  /**
   * @param address foydalanuvchi yozgan manzil ({@code gitlab.com}, {@code https://git.example.uz})
   * @param token foydalanuvchi yozgan token
   */
  GitLabResult add(TelegramUserId user, String address, String token);

  /** Yangi token o'sha GitLab foydalanuvchisiniki bo'lishi kerak. */
  GitLabResult renew(TelegramUserId user, long connectionId, String token);

  GitLabResult remove(TelegramUserId user, long connectionId);
}
