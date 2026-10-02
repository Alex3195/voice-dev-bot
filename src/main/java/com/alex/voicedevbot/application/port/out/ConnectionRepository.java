package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TokenInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GitLab ulanishlari. Token faqat shifrlangan holda saqlanadi. Bitta server + token egasi — bitta
 * ulanish: qayta qo'shilsa token yangilanadi.
 */
public interface ConnectionRepository {

  ProviderConnection save(ServerAddress address, AccessToken token, TokenInfo info);

  /** Server bo'yicha, keyin token egasi bo'yicha tartiblangan. */
  List<ProviderConnection> findAll();

  Optional<ProviderConnection> find(long id);

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
