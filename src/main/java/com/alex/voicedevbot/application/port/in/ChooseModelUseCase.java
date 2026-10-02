package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Optional;

/** Har foydalanuvchining Claude modeli ({@code /model}); tanlanmagan bo'lsa — standarti. */
public interface ChooseModelUseCase {

  /** Whitelist'dan tashqarida — bo'sh. */
  Optional<ModelId> current(TelegramUserId user);

  ModelsResult models(TelegramUserId user);

  /** Faqat ro'yxatdagi model tanlanadi. */
  ModelsResult choose(TelegramUserId user, ModelId model);
}
