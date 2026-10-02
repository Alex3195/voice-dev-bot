package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ModelId;
import java.util.List;
import java.util.Objects;

/** Model tanlash natijasi. */
public sealed interface ModelsResult {

  record Option(ModelId id, String displayName) {

    public Option {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
    }
  }

  record Listed(List<Option> models, ModelId current) implements ModelsResult {

    public Listed {
      models = List.copyOf(models);
      Objects.requireNonNull(current, "current");
    }
  }

  record Chosen(ModelId model) implements ModelsResult {}

  record Failed(LanguageModelProblem problem) implements ModelsResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements ModelsResult {}
}
