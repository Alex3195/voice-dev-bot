package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GitLab ulanishlari. Token faqat shifrlangan holda saqlanadi. Bitta server + token egasi — bitta
 * ulanish: qayta qo'shilsa token yangilanadi.
 */
public interface GitLabConnectionRepository {

  GitLabConnection save(GitLabAddress address, GitLabToken token, TokenInfo info);

  /** Server bo'yicha, keyin token egasi bo'yicha tartiblangan. */
  List<GitLabConnection> findAll();

  Optional<GitLabConnection> find(long id);

  /** Ulanish va unga bog'langan projectlarning repo havolalari o'chiriladi. */
  void remove(long id);

  /**
   * Ogohlantirish shu kuni hali yuborilmagan bo'lsa, uni "yuborildi" deb belgilaydi.
   *
   * @return {@code true} — ogohlantirish yuborish kerak (bot qayta ishga tushsa ham kuniga bir
   *     marta)
   */
  boolean claimExpiryAlert(long id, LocalDate day);
}
