package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.ChooseModelUseCase;
import com.alex.voicedevbot.application.port.in.ModelsResult;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;
import java.util.Optional;

/** {@code ⚙️ → 🤖 Model} va {@code /model}: Claude modelini tanlash. */
public class ModelDialog {

  private final ChooseModelUseCase models;

  public ModelDialog(ChooseModelUseCase models) {
    this.models = Objects.requireNonNull(models, "models");
  }

  static boolean handles(String action) {
    return action.equals(Actions.MODELS) || action.startsWith(Actions.MODEL_PREFIX);
  }

  /** Whitelist'dan tashqarida — bo'sh. */
  Optional<ModelId> current(TelegramUserId user) {
    return models.current(user);
  }

  Optional<Screen> onButton(TelegramUserId user, String action) {
    if (action.equals(Actions.MODELS)) {
      return describe(models.models(user));
    }
    return choose(user, action.substring(Actions.MODEL_PREFIX.length()));
  }

  /**
   * @param id {@code /model} argumenti yoki tugmadagi model
   */
  Optional<Screen> choose(TelegramUserId user, String id) {
    ModelId model;
    try {
      model = new ModelId(id.strip());
    } catch (IllegalArgumentException e) {
      return describe(models.models(user))
          .map(screen -> screen.withNotice("⚠️ Bunday model yo'q: " + Html.code(id.strip())));
    }
    ModelsResult result = models.choose(user, model);
    if (!(result instanceof ModelsResult.Chosen)) {
      return describe(result);
    }
    return describe(models.models(user))
        .map(screen -> screen.withNotice("✅ Model: " + Html.code(model.value())));
  }

  private static Optional<Screen> describe(ModelsResult result) {
    return switch (result) {
      case ModelsResult.Listed listed -> Optional.of(ModelScreens.list(listed));
      case ModelsResult.Chosen(var model) ->
          Optional.of(Screen.text("✅ Model: " + Html.code(model.value())));
      case ModelsResult.Failed(var problem) -> Optional.of(ModelScreens.failed(problem));
      case ModelsResult.AccessDenied() -> Optional.empty();
    };
  }
}
