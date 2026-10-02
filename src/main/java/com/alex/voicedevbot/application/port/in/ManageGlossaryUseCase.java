package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;

/** Faol project lug'atini ko'rish va o'zgartirish. */
public interface ManageGlossaryUseCase {

  GlossaryCommandResult showGlossary(TelegramUserId user);

  /**
   * @throws IllegalArgumentException atama bo'sh yoki juda uzun bo'lsa
   */
  GlossaryCommandResult addTerms(TelegramUserId user, List<String> terms);

  GlossaryCommandResult removeTerms(TelegramUserId user, List<String> terms);
}
