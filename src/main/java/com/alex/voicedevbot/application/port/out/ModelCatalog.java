package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.ModelId;
import java.util.List;
import java.util.Objects;

/** Tanlash mumkin bo'lgan Claude modellari. Xato bo'lsa {@link LanguageModelException}. */
public interface ModelCatalog {

  /** Bot talab qiladigan imkoniyatlari bor modellar, eng yangisi birinchi. */
  List<Model> models();

  record Model(ModelId id, String displayName) {

    public Model {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
    }
  }
}
